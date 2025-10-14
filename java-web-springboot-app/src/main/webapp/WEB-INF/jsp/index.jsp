<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="fmt" uri="http://java.sun.com/jsp/jstl/fmt" %>
<!DOCTYPE html>
<html lang="ko">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Vault Spring Boot Web App</title>
    <style>
        body {
            font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif;
            margin: 0;
            padding: 20px;
            background-color: #f5f5f5;
        }
        .container {
            max-width: 1200px;
            margin: 0 auto;
            background: white;
            padding: 30px;
            border-radius: 10px;
            box-shadow: 0 2px 10px rgba(0,0,0,0.1);
        }
        .header {
            text-align: center;
            margin-bottom: 30px;
            padding-bottom: 20px;
            border-bottom: 2px solid #e0e0e0;
        }
        .section {
            margin-bottom: 30px;
            padding: 20px;
            border: 1px solid #ddd;
            border-radius: 8px;
            background-color: #fafafa;
        }
        .section h3 {
            margin-top: 0;
            color: #333;
            border-bottom: 1px solid #ccc;
            padding-bottom: 10px;
        }
        .secret-item {
            background: white;
            padding: 15px;
            margin: 10px 0;
            border-radius: 5px;
            border-left: 4px solid #4CAF50;
        }
        .secret-item.error {
            border-left-color: #f44336;
            background-color: #ffebee;
        }
        .secret-data {
            font-family: 'Courier New', monospace;
            background-color: #f8f8f8;
            padding: 10px;
            border-radius: 4px;
            margin-top: 10px;
            white-space: pre-wrap;
        }
        .table-info {
            background: white;
            padding: 15px;
            margin: 10px 0;
            border-radius: 5px;
            border-left: 4px solid #2196F3;
        }
        .status-success {
            color: #4CAF50;
            font-weight: bold;
        }
        .status-error {
            color: #f44336;
            font-weight: bold;
        }
        .refresh-btn {
            background-color: #4CAF50;
            color: white;
            padding: 10px 20px;
            border: none;
            border-radius: 4px;
            cursor: pointer;
            font-size: 16px;
            margin: 10px 5px;
        }
        .refresh-btn:hover {
            background-color: #45a049;
        }
        .auto-refresh {
            text-align: center;
            margin: 20px 0;
            padding: 15px;
            background-color: #e3f2fd;
            border-radius: 5px;
        }
        .timestamp {
            color: #666;
            font-size: 12px;
            margin-top: 10px;
        }
    </style>
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
            <h1>🚀 Vault Spring Boot Web App</h1>
            <p>Spring Cloud Vault Config를 사용한 시크릿 관리 웹 애플리케이션</p>
        </div>

        <div class="auto-refresh">
            <p>🔄 자동 새로고침: 30초마다 페이지가 자동으로 갱신됩니다</p>
            <button class="refresh-btn" onclick="location.reload()">수동 새로고침</button>
            <a href="/vault-web/refresh" class="refresh-btn" style="text-decoration: none; display: inline-block;">시크릿 갱신</a>
            <a href="/vault-web/database" class="refresh-btn" style="text-decoration: none; display: inline-block;">Database 정보</a>
            <a href="/vault-web/health" class="refresh-btn" style="text-decoration: none; display: inline-block;">Health Check</a>
        </div>

        <c:if test="${not empty error}">
            <div class="section">
                <h3>❌ 오류 발생</h3>
                <div class="secret-item error">
                    <strong>오류 메시지:</strong> ${error}
                </div>
            </div>
        </c:if>

        <!-- Vault 시크릿 정보 -->
        <div class="section">
            <h3>🔐 Vault 시크릿 정보</h3>
            
            <c:if test="${not empty secrets}">
                <c:forEach var="entry" items="${secrets}">
                    <div class="secret-item ${entry.value.data.error != null ? 'error' : ''}">
                        <h4>${entry.value.type} - ${entry.value.path}</h4>
                        <p><strong>마지막 업데이트:</strong> ${entry.value.lastUpdated}</p>
                        <c:if test="${not empty entry.value.version}">
                            <p><strong>버전:</strong> ${entry.value.version}</p>
                        </c:if>
                        <c:if test="${not empty entry.value.ttl}">
                            <p><strong>TTL:</strong> ${entry.value.ttl}초</p>
                        </c:if>
                        <c:if test="${not empty entry.value.leaseId}">
                            <p><strong>Lease ID:</strong> ${entry.value.leaseId}</p>
                        </c:if>
                        <p><strong>갱신 가능:</strong> ${entry.value.renewable ? 'Yes' : 'No'}</p>
                        
                        <div class="secret-data">
                            <c:choose>
                                <c:when test="${entry.value.data.error != null}">
                                    <span class="status-error">❌ ${entry.value.data.error}</span>
                                </c:when>
                                <c:otherwise>
                                    <c:forEach var="dataEntry" items="${entry.value.data}">
                                        <strong>${dataEntry.key}:</strong> ${dataEntry.value}<br/>
                                    </c:forEach>
                                </c:otherwise>
                            </c:choose>
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
                        <p><strong>데이터베이스:</strong> ${dbConnection.database_product} ${dbConnection.database_version}</p>
                        <p><strong>드라이버:</strong> ${dbConnection.driver_name} ${dbConnection.driver_version}</p>
                        <p><strong>URL:</strong> ${dbConnection.url}</p>
                        <p><strong>사용자:</strong> ${dbConnection.username}</p>
                    </c:if>
                    <c:if test="${dbConnection.status == 'error'}">
                        <p><strong>오류:</strong> ${dbConnection.error}</p>
                    </c:if>
                </div>
            </c:if>
        </div>

        <!-- Database 테이블 정보 -->
        <div class="section">
            <h3>📋 Database 테이블 정보</h3>
            
            <c:if test="${not empty tables}">
                <p><strong>총 ${tables.size()}개의 테이블/뷰가 있습니다.</strong></p>
                <c:forEach var="table" items="${tables}">
                    <div class="table-info">
                        <h4>📊 ${table.table_name}</h4>
                        <p><strong>타입:</strong> ${table.table_type}</p>
                        <p><strong>스키마:</strong> ${table.table_schema}</p>
                        <p><strong>카탈로그:</strong> ${table.table_catalog}</p>
                        <c:if test="${not empty table.remarks}">
                            <p><strong>설명:</strong> ${table.remarks}</p>
                        </c:if>
                    </div>
                </c:forEach>
            </c:if>
        </div>

        <!-- Database 통계 -->
        <c:if test="${not empty dbStats}">
            <div class="section">
                <h3>📊 Database 통계</h3>
                <div class="table-info">
                    <p><strong>최대 연결 수:</strong> ${dbStats.max_connections}</p>
                    <p><strong>테이블당 최대 컬럼 수:</strong> ${dbStats.max_columns_in_table}</p>
                    <p><strong>인덱스당 최대 컬럼 수:</strong> ${dbStats.max_columns_in_index}</p>
                    <p><strong>SELECT 최대 컬럼 수:</strong> ${dbStats.max_columns_in_select}</p>
                    <p><strong>총 테이블 수:</strong> ${dbStats.table_count}</p>
                </div>
            </div>
        </c:if>

        <div class="timestamp">
            <p>페이지 로드 시간: <fmt:formatDate value="<%=new java.util.Date()%>" pattern="yyyy-MM-dd HH:mm:ss"/></p>
        </div>
    </div>
</body>
</html>
