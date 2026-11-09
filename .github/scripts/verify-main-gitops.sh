#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
publisher="${repo_root}/.github/scripts/publish-main-image.sh"

fail() {
  printf 'FAIL: %s\n' "$1" >&2
  exit 1
}

[[ -x "${publisher}" ]] || fail "missing executable publish-main-image.sh"

test_root="$(mktemp -d)"
trap 'rm -rf "${test_root}"' EXIT
fake_bin="${test_root}/bin"
call_log="${test_root}/calls.log"
mkdir -p "${fake_bin}"

cat >"${fake_bin}/docker" <<'FAKE'
#!/usr/bin/env bash
set -euo pipefail
printf 'docker %s\n' "$*" >>"${CALL_LOG}"
FAKE

cat >"${fake_bin}/aws" <<'FAKE'
#!/usr/bin/env bash
set -euo pipefail
printf 'aws %s\n' "$*" >>"${CALL_LOG}"
if [[ "$*" == *"ecr describe-images"* ]]; then
  printf 'sha256:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa\n'
fi
FAKE

cat >"${fake_bin}/gh" <<'FAKE'
#!/usr/bin/env bash
set -euo pipefail
printf 'gh %s\n' "$*" >>"${CALL_LOG}"
FAKE
chmod +x "${fake_bin}"/*

sha="0123456789abcdef0123456789abcdef01234567"
PATH="${fake_bin}:${PATH}" \
CALL_LOG="${call_log}" \
AWS_REGION="ap-northeast-2" \
AWS_ACCOUNT_ID="352635536602" \
ECR_REPOSITORY="cosmos-v2/spring" \
IMAGE_TAG="${sha}" \
LOCAL_IMAGE="cosmos-backend:${sha}" \
COMPONENT="spring" \
SOURCE_REPOSITORY="cosmos-v2/spring" \
SOURCE_REPO_SLUG="100-hours-a-week/KTB4-9th-BE" \
CLOUD_REPOSITORY="100-hours-a-week/KTB4-9th-Cloud" \
GH_TOKEN="test-token" \
"${publisher}"

call1="$(sed -n '1p' "${call_log}")"
call2="$(sed -n '2p' "${call_log}")"
call3="$(sed -n '3p' "${call_log}")"
call4="$(sed -n '4p' "${call_log}")"
[[ "${call1}" == "docker tag cosmos-backend:${sha} 352635536602.dkr.ecr.ap-northeast-2.amazonaws.com/cosmos-v2/spring:${sha}" ]] || fail "must tag the already-scanned local image"
[[ "${call2}" == "docker push 352635536602.dkr.ecr.ap-northeast-2.amazonaws.com/cosmos-v2/spring:${sha}" ]] || fail "must push the immutable SHA image"
[[ "${call3}" == *"aws ecr describe-images"*"imageTag=${sha}"* ]] || fail "must resolve the pushed image digest"
[[ "${call4}" == *"gh api --method POST repos/100-hours-a-week/KTB4-9th-Cloud/dispatches"* ]] || fail "must dispatch to Cloud"
[[ "${call4}" == *"event_type=promote-v3"* ]] || fail "must emit promote-v3"
[[ "${call4}" == *"client_payload[component]=spring"* ]] || fail "must identify spring"
[[ "${call4}" == *"client_payload[repository]=cosmos-v2/spring"* ]] || fail "must identify the source ECR repository"
[[ "${call4}" == *"client_payload[image_tag]=${sha}"* ]] || fail "must forward the full source revision"
[[ "${call4}" == *"client_payload[source_repository]=100-hours-a-week/KTB4-9th-BE"* ]] || fail "must identify the source GitHub repository"

if PATH="${fake_bin}:${PATH}" CALL_LOG="${call_log}" AWS_REGION=ap-northeast-2 AWS_ACCOUNT_ID=352635536602 ECR_REPOSITORY=cosmos-v2/spring IMAGE_TAG=bad LOCAL_IMAGE=test COMPONENT=spring SOURCE_REPOSITORY=cosmos-v2/spring SOURCE_REPO_SLUG=100-hours-a-week/KTB4-9th-BE CLOUD_REPOSITORY=100-hours-a-week/KTB4-9th-Cloud GH_TOKEN=test "${publisher}" >/dev/null 2>&1; then
  fail "must reject a non-SHA image tag"
fi

printf 'PASS: backend main GitOps publication contract\n'
