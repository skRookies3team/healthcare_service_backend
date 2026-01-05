#!/bin/bash
# =============================================================================
# Healthcare Service Gateway 연동 테스트 스크립트
# 
# 사용법: ./test-gateway-integration.sh [JWT_TOKEN]
#
# WHY: Gateway를 통한 Healthcare 서비스 연동 상태를 자동으로 검증
# =============================================================================

set -e

# 색상 정의
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# 설정
GATEWAY_URL="${GATEWAY_URL:-http://localhost:8000}"
HEALTHCARE_URL="${HEALTHCARE_URL:-http://localhost:8085}"
JWT_TOKEN="$1"

echo "=============================================="
echo "  Healthcare Gateway Integration Test"
echo "=============================================="
echo ""
echo "Gateway URL: $GATEWAY_URL"
echo "Healthcare URL: $HEALTHCARE_URL"
echo ""

PASS_COUNT=0
FAIL_COUNT=0

# 테스트 함수
test_endpoint() {
    local name="$1"
    local expected_code="$2"
    local actual_code="$3"
    
    if [ "$actual_code" -eq "$expected_code" ]; then
        echo -e "${GREEN}✅ PASS${NC} - $name (HTTP $actual_code)"
        ((PASS_COUNT++))
    else
        echo -e "${RED}❌ FAIL${NC} - $name (Expected: $expected_code, Got: $actual_code)"
        ((FAIL_COUNT++))
    fi
}

# -----------------------------------------------------------------------------
# Test 1: Healthcare 직접 헬스체크
# -----------------------------------------------------------------------------
echo -e "\n${YELLOW}[1/5] Healthcare 직접 헬스체크${NC}"
HTTP_CODE=$(curl -s -o /dev/null -w "%{http_code}" "$HEALTHCARE_URL/api/chat/health" 2>/dev/null || echo "000")
test_endpoint "Direct Healthcare Health Check" 200 "$HTTP_CODE"

if [ "$HTTP_CODE" -eq "000" ]; then
    echo -e "${RED}⚠️  Healthcare 서비스가 실행 중이 아닙니다. 테스트 중단.${NC}"
    exit 1
fi

# -----------------------------------------------------------------------------
# Test 2: Gateway 헬스체크
# -----------------------------------------------------------------------------
echo -e "\n${YELLOW}[2/5] Gateway → Healthcare 헬스체크 (Public)${NC}"
HTTP_CODE=$(curl -s -o /dev/null -w "%{http_code}" "$GATEWAY_URL/api/chat/health" 2>/dev/null || echo "000")
test_endpoint "Gateway Health Check" 200 "$HTTP_CODE"

if [ "$HTTP_CODE" -eq "000" ]; then
    echo -e "${RED}⚠️  Gateway가 실행 중이 아니거나 Healthcare 라우트가 설정되지 않았습니다.${NC}"
    echo -e "${YELLOW}💡 GATEWAY_INTEGRATION.md 파일을 참고하여 Gateway 설정을 완료하세요.${NC}"
fi

# -----------------------------------------------------------------------------
# Test 3: 인증 없이 Private 엔드포인트 호출 (401 예상)
# -----------------------------------------------------------------------------
echo -e "\n${YELLOW}[3/5] 인증 없이 채팅 요청 (401 예상)${NC}"
HTTP_CODE=$(curl -s -o /dev/null -w "%{http_code}" \
    -X POST "$GATEWAY_URL/api/chat/haiku" \
    -H "Content-Type: application/json" \
    -d '{"message": "test"}' 2>/dev/null || echo "000")
test_endpoint "Unauthorized Request" 401 "$HTTP_CODE"

# -----------------------------------------------------------------------------
# Test 4-5: JWT 토큰이 제공된 경우 인증 테스트
# -----------------------------------------------------------------------------
if [ -n "$JWT_TOKEN" ]; then
    echo -e "\n${YELLOW}[4/5] JWT 토큰으로 Haiku 채팅 요청${NC}"
    HTTP_CODE=$(curl -s -o /dev/null -w "%{http_code}" \
        -X POST "$GATEWAY_URL/api/chat/haiku" \
        -H "Content-Type: application/json" \
        -H "Authorization: Bearer $JWT_TOKEN" \
        -d '{"message": "강아지 건강 팁"}' 2>/dev/null || echo "000")
    test_endpoint "Authorized Haiku Chat" 200 "$HTTP_CODE"
    
    echo -e "\n${YELLOW}[5/5] JWT 토큰으로 Persona 채팅 요청${NC}"
    HTTP_CODE=$(curl -s -o /dev/null -w "%{http_code}" \
        -X POST "$GATEWAY_URL/api/chat/persona" \
        -H "Content-Type: application/json" \
        -H "Authorization: Bearer $JWT_TOKEN" \
        -d '{"petId": 1, "message": "오늘 기분 어때?"}' 2>/dev/null || echo "000")
    # 200 또는 400 (petId가 없는 경우) 모두 인증 성공으로 간주
    if [ "$HTTP_CODE" -eq 200 ] || [ "$HTTP_CODE" -eq 400 ]; then
        echo -e "${GREEN}✅ PASS${NC} - Authorized Persona Chat (HTTP $HTTP_CODE - Auth OK)"
        ((PASS_COUNT++))
    else
        echo -e "${RED}❌ FAIL${NC} - Authorized Persona Chat (Expected: 200/400, Got: $HTTP_CODE)"
        ((FAIL_COUNT++))
    fi
else
    echo -e "\n${YELLOW}[4/5] JWT 토큰 미제공 - 인증 테스트 건너뜀${NC}"
    echo -e "${YELLOW}[5/5] JWT 토큰 미제공 - 인증 테스트 건너뜀${NC}"
    echo ""
    echo -e "${YELLOW}💡 전체 테스트를 수행하려면 JWT 토큰을 제공하세요:${NC}"
    echo "   ./test-gateway-integration.sh <JWT_TOKEN>"
fi

# -----------------------------------------------------------------------------
# 결과 요약
# -----------------------------------------------------------------------------
echo ""
echo "=============================================="
echo "  테스트 결과 요약"
echo "=============================================="
echo -e "통과: ${GREEN}$PASS_COUNT${NC}"
echo -e "실패: ${RED}$FAIL_COUNT${NC}"

if [ "$FAIL_COUNT" -eq 0 ]; then
    echo ""
    echo -e "${GREEN}🎉 모든 테스트 통과!${NC}"
    exit 0
else
    echo ""
    echo -e "${RED}⚠️  일부 테스트 실패. 위 메시지를 확인하세요.${NC}"
    exit 1
fi
