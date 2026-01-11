### 사용 시기

중요: 서드파티 라이브러리 사용 시 항상 Context7에 먼저 쿼리하여 최신 문서를 가져오고 API 환각을 방지하세요.

### 워크플로우

1. `resolve-library-id` → Context7 ID 가져오기 (사용자가 `/org/project` 형식을 제공하지 않는 경우)
2. `get-library-docs` → 문서 가져오기
