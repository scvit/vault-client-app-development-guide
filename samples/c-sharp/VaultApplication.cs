using System;
using System.Collections.Generic;
using System.Text.Json;
using System.Threading;
using System.Threading.Tasks;
using Microsoft.Extensions.Logging;

namespace VaultCsharpApp
{
    /// <summary>
    /// Vault 클라이언트 애플리케이션
    /// </summary>
    public class VaultApplication
    {
        private readonly VaultConfig      _configLoader;
        private readonly AppConfig        _config;
        private readonly VaultClient      _vaultClient;
        private readonly ILogger<VaultApplication> _logger;

        private CancellationTokenSource _cts = new();
        private readonly List<Task>     _tasks = new();

        /// <summary>
        /// 애플리케이션 초기화
        /// </summary>
        /// <param name="configFile">설정 파일 경로 (기본값: config.ini)</param>
        /// <param name="loggerFactory">로거 팩토리 (null이면 콘솔 로거 사용)</param>
        public VaultApplication(
            string configFile = "config.ini",
            ILoggerFactory? loggerFactory = null)
        {
            loggerFactory ??= LoggerFactory.Create(b =>
                b.AddSimpleConsole(o => o.TimestampFormat = "yyyy-MM-dd HH:mm:ss ")
                 .SetMinimumLevel(LogLevel.Information));

            _logger = loggerFactory.CreateLogger<VaultApplication>();

            _configLoader = new VaultConfig(configFile);
            _config       = _configLoader.GetAllConfig();
            _vaultClient  = new VaultClient(
                _config.Vault,
                loggerFactory.CreateLogger<VaultClient>());

            // Ctrl+C / SIGTERM 처리
            Console.CancelKeyPress += (_, e) =>
            {
                e.Cancel = true; // 프로세스 즉시 종료 방지
                _logger.LogInformation("Ctrl+C 수신, 애플리케이션 종료 중...");
                Stop();
            };
        }

        // ── 시작 / 중지 ──────────────────────────────────────────

        /// <summary>
        /// 애플리케이션 시작
        /// </summary>
        /// <returns>정상 종료 여부</returns>
        public async Task<bool> StartAsync()
        {
            _logger.LogInformation("🚀 Vault C# 클라이언트 애플리케이션 시작");

            // Vault 로그인
            if (!await _vaultClient.LoginAsync())
            {
                _logger.LogError("Vault 로그인 실패");
                return false;
            }

            PrintStartupInfo();
            StartSchedulers();

            // CancellationToken이 취소될 때까지 대기
            try
            {
                await Task.Delay(Timeout.Infinite, _cts.Token);
            }
            catch (TaskCanceledException)
            {
                // 정상 종료
            }
            finally
            {
                await StopAsync();
            }

            return true;
        }

        /// <summary>
        /// 동기 진입점용 래퍼 (Program.cs에서 호출)
        /// </summary>
        public void Start() => StartAsync().GetAwaiter().GetResult();

        /// <summary>
        /// 취소 토큰 발행 (시그널 핸들러에서 호출)
        /// </summary>
        public void Stop() => _cts.Cancel();

        /// <summary>
        /// 모든 스케줄러 Task 종료 대기
        /// </summary>
        private async Task StopAsync()
        {
            _logger.LogInformation("애플리케이션 중지 중...");

            // 모든 스케줄러 Task 종료 대기 (최대 5초)
            await Task.WhenAll(_tasks).WaitAsync(TimeSpan.FromSeconds(5))
                  .ContinueWith(_ => { }); // 타임아웃 예외 무시

            _logger.LogInformation("애플리케이션 종료 완료");
        }

        // ── 시작 정보 출력 ───────────────────────────────────────

        /// <summary>
        /// 시작 정보 출력
        /// </summary>
        private void PrintStartupInfo()
        {
            Console.WriteLine("🚀 Vault C# 클라이언트 애플리케이션 시작");
            Console.WriteLine("✅ Vault 로그인 성공");

            // 설정 정보 출력
            _configLoader.PrintConfig();

            Console.WriteLine("\n📖 예제 목적 및 사용 시나리오");
            Console.WriteLine("이 예제는 Vault 연동 개발을 위한 참고용 애플리케이션입니다.");
            Console.WriteLine("애플리케이션 초기 구동에만 필요한 경우 처음 한번만 API 호출하고 나면 이후 구동시 캐시를 활용하여 메모리 사용을 줄입니다.");
            Console.WriteLine("예제에서는 주기적으로 계속 시크릿을 가져와 갱신하도록 구현되어 있습니다.");

            Console.WriteLine("\n🔧 지원 기능:");
            Console.WriteLine("- KV v2 시크릿 엔진 (버전 기반 캐싱)");
            Console.WriteLine("- Database Dynamic 시크릿 엔진 (TTL 기반 갱신)");
            Console.WriteLine("- Database Static 시크릿 엔진 (시간 기반 캐싱)");
            Console.WriteLine("- 자동 토큰 갱신");
            Console.WriteLine("- Entity 기반 권한 관리");
            Console.WriteLine("- Vault Namespace 지원");

            Console.WriteLine("\n🔄 시크릿 갱신 시작... (Ctrl+C로 종료)");
        }

        // ── 스케줄러 시작 ────────────────────────────────────────

        /// <summary>
        /// 활성화된 스케줄러를 백그라운드 Task로 시작
        /// </summary>
        private void StartSchedulers()
        {
            var token = _cts.Token;

            // KV 시크릿 갱신 스케줄러
            if (_config.KvSecret.Enabled)
            {
                _tasks.Add(Task.Run(() => KvSecretSchedulerAsync(token), token));
                _logger.LogInformation("✅ KV 시크릿 갱신 스케줄러 시작 (간격: {Interval}초)",
                    _config.KvSecret.RefreshInterval);
            }

            // Database Dynamic 시크릿 갱신 스케줄러
            if (_config.DatabaseDynamic.Enabled)
            {
                _tasks.Add(Task.Run(() => DatabaseDynamicSchedulerAsync(token), token));
                _logger.LogInformation("✅ Database Dynamic 시크릿 갱신 스케줄러 시작 (간격: 5초)");
            }

            // Database Static 시크릿 갱신 스케줄러
            if (_config.DatabaseStatic.Enabled)
            {
                _tasks.Add(Task.Run(() => DatabaseStaticSchedulerAsync(token), token));
                _logger.LogInformation("✅ Database Static 시크릿 갱신 스케줄러 시작 (간격: 10초)");
            }
        }

        // ── 스케줄러 루프들 ──────────────────────────────────────

        /// <summary>
        /// KV 시크릿 갱신 루프
        /// </summary>
        /// <param name="ct">취소 토큰</param>
        private async Task KvSecretSchedulerAsync(CancellationToken ct)
        {
            while (!ct.IsCancellationRequested)
            {
                try
                {
                    Console.WriteLine("\n=== KV Secret Refresh ===");

                    // Vault에서 KV 시크릿 조회
                    var secretData = await _vaultClient.GetKvSecretAsync(_config.KvSecret.Path);

                    if (secretData != null)
                    {
                        Console.WriteLine("✅ KV 시크릿 조회 성공");
                        Console.WriteLine("📦 KV Secret Data:");
                        Console.WriteLine(JsonSerializer.Serialize(secretData,
                            new JsonSerializerOptions { WriteIndented = true }));
                    }
                    else
                    {
                        Console.WriteLine("❌ KV 시크릿 조회 실패");
                    }

                    await Task.Delay(_config.KvSecret.RefreshInterval * 1000, ct);
                }
                catch (TaskCanceledException) { break; }
                catch (Exception e)
                {
                    _logger.LogError("KV 시크릿 갱신 중 오류: {Error}", e.Message);
                    await Task.Delay(5000, ct).ContinueWith(_ => { });
                }
            }
        }

        /// <summary>
        /// Database Dynamic 시크릿 갱신 루프
        /// </summary>
        /// <param name="ct">취소 토큰</param>
        private async Task DatabaseDynamicSchedulerAsync(CancellationToken ct)
        {
            while (!ct.IsCancellationRequested)
            {
                try
                {
                    Console.WriteLine("\n=== Database Dynamic Secret Refresh ===");

                    // Vault에서 Database Dynamic 시크릿 조회 (매번 새 자격증명 발급 또는 캐시 반환)
                    var result = await _vaultClient.GetDatabaseDynamicSecretAsync(
                        _config.DatabaseDynamic.RoleId);

                    if (result != null)
                    {
                        Console.WriteLine($"✅ Database Dynamic 시크릿 조회 성공 (TTL: {result.Ttl}초)");
                        Console.WriteLine($"🗄️ Database Dynamic Secret (TTL: {result.Ttl}초):");
                        Console.WriteLine($"  username: {result.Data["username"]}");
                        Console.WriteLine($"  password: {result.Data["password"]}");
                    }
                    else
                    {
                        Console.WriteLine("❌ Database Dynamic 시크릿 조회 실패");
                    }

                    await Task.Delay(5000, ct);
                }
                catch (TaskCanceledException) { break; }
                catch (Exception e)
                {
                    _logger.LogError("Database Dynamic 시크릿 갱신 중 오류: {Error}", e.Message);
                    await Task.Delay(5000, ct).ContinueWith(_ => { });
                }
            }
        }

        /// <summary>
        /// Database Static 시크릿 갱신 루프
        /// </summary>
        /// <param name="ct">취소 토큰</param>
        private async Task DatabaseStaticSchedulerAsync(CancellationToken ct)
        {
            while (!ct.IsCancellationRequested)
            {
                try
                {
                    Console.WriteLine("\n=== Database Static Secret Refresh ===");

                    // Vault에서 Database Static 시크릿 조회 (고정 자격증명, 캐시 활용)
                    var result = await _vaultClient.GetDatabaseStaticSecretAsync(
                        _config.DatabaseStatic.RoleId);

                    if (result != null)
                    {
                        Console.WriteLine($"✅ Database Static 시크릿 조회 성공 (TTL: {result.Ttl}초)");
                        Console.WriteLine($"🔒 Database Static Secret (TTL: {result.Ttl}초):");
                        Console.WriteLine($"  username: {result.Data["username"]}");
                        Console.WriteLine($"  password: {result.Data["password"]}");
                    }
                    else
                    {
                        Console.WriteLine("❌ Database Static 시크릿 조회 실패");
                    }

                    await Task.Delay(10000, ct);
                }
                catch (TaskCanceledException) { break; }
                catch (Exception e)
                {
                    _logger.LogError("Database Static 시크릿 갱신 중 오류: {Error}", e.Message);
                    await Task.Delay(10000, ct).ContinueWith(_ => { });
                }
            }
        }
    }
}
