<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="fmt" uri="http://java.sun.com/jsp/jstl/fmt" %>
<!DOCTYPE html>
<html lang="ko">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Vault Tomcat Web App</title>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/css/style.css">
    <script>
        // 자동 새로고침 (30초마다)
        setInterval(function() {
            location.reload();
        }, 30000);
    </script>
</head>
<body>
    <div class="container">
        <div class="header">
            <h1>🚀 Vault Tomcat Web App</h1>
            <p>Tomcat 10에서 실행되는 Vault 연동 Java Web Application</p>
        </div>

        <div class="auto-refresh">
            <p>🔄 자동 새로고침: 30초마다 페이지가 자동으로 갱신됩니다</p>
            <button class="refresh-btn" onclick="location.reload()">수동 새로고침</button>
            <a href="${pageContext.request.contextPath}/refresh" class="refresh-btn">시크릿 갱신</a>
        </div>

        <c:if test="${not empty error}">
            <div class="section">
                <h3>❌ 오류 발생</h3>
                <div class="secret-item error">
                    <strong>오류 메시지:</strong> <span>${error}</span>
                </div>
            </div>
        </c:if>

        <c:if test="${not empty vaultError}">
            <div class="section">
                <h3>⚠️ Vault 오류</h3>
                <div class="secret-item error">
                    <strong>Vault 오류:</strong> <span>${vaultError}</span>
                </div>
            </div>
        </c:if>

        <!-- Servlet 테스트 데이터 -->
        <c:if test="${not empty testData}">
            <div class="section">
                <h3>🧪 Servlet 테스트 데이터</h3>
                <div class="secret-item">
                    <h4>정적 데이터 테스트</h4>
                    <p><strong>메시지:</strong> <span>${testData.message}</span></p>
                    <p><strong>시간:</strong> <span>${testData.timestamp}</span></p>
                    <p><strong>상태:</strong> <span>${testData.status}</span></p>
                </div>
            </div>
        </c:if>

        <!-- Vault 시크릿 정보 -->
        <div class="section">
            <h3>🔐 Vault 시크릿 정보</h3>
            
            <c:if test="${not empty secrets}">
                <c:forEach var="entry" items="${secrets}">
                    <div class="secret-item">
                        <h4>${entry.value.type} - ${entry.value.path}</h4>
                        <p><strong>마지막 업데이트:</strong> <span><fmt:formatDate value="${entry.value.lastUpdated}" pattern="yyyy-MM-dd HH:mm:ss"/></span></p>
                        <c:if test="${not empty entry.value.version}">
                            <p><strong>버전:</strong> <span>${entry.value.version}</span></p>
                        </c:if>
                        <c:if test="${not empty entry.value.ttl}">
                            <p><strong>TTL:</strong> <span>${entry.value.ttl}</span>초</p>
                        </c:if>
                        <c:if test="${not empty entry.value.leaseId}">
                            <p><strong>Lease ID:</strong> <span>${entry.value.leaseId}</span></p>
                        </c:if>
                        <p><strong>갱신 가능:</strong> <span>${entry.value.renewable ? 'Yes' : 'No'}</span></p>
                        
                        <div class="secret-data">
                            <c:forEach var="dataEntry" items="${entry.value.data}" varStatus="status">
                                <strong>${dataEntry.key}</strong>: 
                                <c:choose>
                                    <c:when test="${not empty dataEntry.value}">
                                        <span>${dataEntry.value}</span>
                                    </c:when>
                                    <c:otherwise>
                                        <span style="color: #999; font-style: italic;">(empty)</span>
                                    </c:otherwise>
                                </c:choose>
                                <c:if test="${!status.last}"> | </c:if>
                            </c:forEach>
                        </div>
                    </div>
                </c:forEach>
            </c:if>
        </div>

        <!-- Database 연결 정보 -->
        <div class="section">
            <h3>🗄️ Database 연결 정보</h3>
            
            <c:if test="${not empty dbConnection}">
                <div class="table-info">
                    <h4>연결 상태</h4>
                    <p><strong>상태:</strong> 
                        <span class="${dbConnection.status == 'success' ? 'status-success' : 'status-error'}">
                            ${dbConnection.status == 'success' ? '✅ 연결 성공' : '❌ 연결 실패'}
                        </span>
                    </p>
                    <c:if test="${dbConnection.status == 'success'}">
                        <p><strong>데이터베이스:</strong> <span>${dbConnection.database_product} ${dbConnection.database_version}</span></p>
                        <p><strong>드라이버:</strong> <span>${dbConnection.driver_name} ${dbConnection.driver_version}</span></p>
                        <p><strong>URL:</strong> <span>${dbConnection.url}</span></p>
                        <p><strong>사용자:</strong> <span>${dbConnection.username}</span></p>
                    </c:if>
                    <c:if test="${dbConnection.status == 'error'}">
                        <p><strong>오류:</strong> <span>${dbConnection.error}</span></p>
                    </c:if>
                </div>
            </c:if>
        </div>

        <!-- Database 통계 -->
        <c:if test="${not empty dbStats}">
            <div class="section">
                <h3>📊 Database 통계</h3>
                <div class="table-info">
                    <p><strong>최대 연결 수:</strong> <span>${dbStats.max_connections}</span></p>
                    <p><strong>테이블당 최대 컬럼 수:</strong> <span>${dbStats.max_columns_in_table}</span></p>
                    <p><strong>인덱스당 최대 컬럼 수:</strong> <span>${dbStats.max_columns_in_index}</span></p>
                    <p><strong>SELECT 최대 컬럼 수:</strong> <span>${dbStats.max_columns_in_select}</span></p>
                    <p><strong>총 테이블 수:</strong> <span>${dbStats.table_count}</span></p>
                </div>
            </div>
        </c:if>

        <div class="timestamp">
            <p>페이지 로드 시간: <span><fmt:formatDate value="<%=new java.util.Date()%>" pattern="yyyy-MM-dd HH:mm:ss"/></span></p>
        </div>
    </div>
</body>
</html>
