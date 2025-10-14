#!/usr/bin/env python3
# -*- coding: utf-8 -*-

"""
Vault Python Client Application
메인 애플리케이션 로직 및 스케줄러
"""

import time
import signal
import threading
import logging
import json
from typing import Dict, Any
from config_loader import VaultConfig
from vault_client import VaultClient


class VaultApplication:
    """Vault 클라이언트 애플리케이션"""
    
    def __init__(self, config_file: str = "config.ini"):
        """
        애플리케이션 초기화
        
        Args:
            config_file: 설정 파일 경로
        """
        self.config_loader = VaultConfig(config_file)
        self.config = self.config_loader.get_all_config()
        self.vault_client = VaultClient(self.config['vault'])
        
        # 스케줄러 상태
        self.running = False
        self.threads = []
        
        # 로깅 설정
        self.logger = logging.getLogger(__name__)
        self._setup_logging()
        
        # 시그널 핸들러 설정
        signal.signal(signal.SIGINT, self._signal_handler)
        signal.signal(signal.SIGTERM, self._signal_handler)
    
    def _setup_logging(self):
        """로깅 설정"""
        logging.basicConfig(
            level=logging.INFO,
            format='%(asctime)s - %(name)s - %(levelname)s - %(message)s',
            handlers=[
                logging.StreamHandler(),
                logging.FileHandler('vault-python-app.log')
            ]
        )
    
    def _signal_handler(self, signum, frame):
        """시그널 핸들러"""
        self.logger.info(f"시그널 {signum} 수신, 애플리케이션 종료 중...")
        self.stop()
    
    def start(self):
        """애플리케이션 시작"""
        self.logger.info("🚀 Vault Python 클라이언트 애플리케이션 시작")
        
        # Vault 로그인
        if not self.vault_client.login():
            self.logger.error("Vault 로그인 실패")
            return False
        
        # 설정 정보 출력
        self._print_startup_info()
        
        # 스케줄러 시작
        self.running = True
        self._start_schedulers()
        
        # 메인 루프
        try:
            while self.running:
                time.sleep(1)
        except KeyboardInterrupt:
            self.logger.info("키보드 인터럽트 수신")
        finally:
            self.stop()
        
        return True
    
    def stop(self):
        """애플리케이션 중지"""
        self.logger.info("애플리케이션 중지 중...")
        self.running = False
        
        # 모든 스레드 종료 대기
        for thread in self.threads:
            if thread.is_alive():
                thread.join(timeout=5)
        
        self.logger.info("애플리케이션 종료 완료")
    
    def _print_startup_info(self):
        """시작 정보 출력"""
        print("🚀 Vault Python 클라이언트 애플리케이션 시작")
        print("✅ Vault 로그인 성공")
        
        # 설정 정보 출력
        self.config_loader.print_config()
        
        print("\n📖 예제 목적 및 사용 시나리오")
        print("이 예제는 Vault 연동 개발을 위한 참고용 애플리케이션입니다.")
        print("애플리케이션 초기 구동에만 필요한 경우 처음 한번만 API 호출하고 나면 이후 구동시 캐시를 활용하여 메모리 사용을 줄입니다.")
        print("예제에서는 주기적으로 계속 시크릿을 가져와 갱신하도록 구현되어 있습니다.")
        
        print("\n🔧 지원 기능:")
        print("- KV v2 시크릿 엔진 (버전 기반 캐싱)")
        print("- Database Dynamic 시크릿 엔진 (TTL 기반 갱신)")
        print("- Database Static 시크릿 엔진 (시간 기반 캐싱)")
        print("- 자동 토큰 갱신")
        print("- Entity 기반 권한 관리")
        
        print("\n🔄 시크릿 갱신 시작... (Ctrl+C로 종료)")
    
    def _start_schedulers(self):
        """스케줄러 시작"""
        # KV 시크릿 갱신 스케줄러
        if self.config['kv_secret']['enabled']:
            kv_thread = threading.Thread(
                target=self._kv_secret_scheduler,
                name="KV-Secret-Scheduler"
            )
            kv_thread.daemon = True
            kv_thread.start()
            self.threads.append(kv_thread)
            self.logger.info(f"✅ KV 시크릿 갱신 스케줄러 시작 (간격: {self.config['kv_secret']['refresh_interval']}초)")
        
        # Database Dynamic 시크릿 갱신 스케줄러
        if self.config['database_dynamic']['enabled']:
            db_dynamic_thread = threading.Thread(
                target=self._database_dynamic_scheduler,
                name="DB-Dynamic-Scheduler"
            )
            db_dynamic_thread.daemon = True
            db_dynamic_thread.start()
            self.threads.append(db_dynamic_thread)
            self.logger.info("✅ Database Dynamic 시크릿 갱신 스케줄러 시작 (간격: 5초)")
        
        # Database Static 시크릿 갱신 스케줄러
        if self.config['database_static']['enabled']:
            db_static_thread = threading.Thread(
                target=self._database_static_scheduler,
                name="DB-Static-Scheduler"
            )
            db_static_thread.daemon = True
            db_static_thread.start()
            self.threads.append(db_static_thread)
            self.logger.info("✅ Database Static 시크릿 갱신 스케줄러 시작 (간격: 10초)")
    
    def _kv_secret_scheduler(self):
        """KV 시크릿 갱신 스케줄러"""
        while self.running:
            try:
                print("\n=== KV Secret Refresh ===")
                
                secret_data = self.vault_client.get_kv_secret(
                    self.config['kv_secret']['path']
                )
                
                if secret_data:
                    print(f"✅ KV 시크릿 조회 성공")
                    print(f"📦 KV Secret Data:")
                    print(json.dumps(secret_data, indent=2, ensure_ascii=False))
                else:
                    print("❌ KV 시크릿 조회 실패")
                
                time.sleep(self.config['kv_secret']['refresh_interval'])
                
            except Exception as e:
                self.logger.error(f"KV 시크릿 갱신 중 오류: {e}")
                time.sleep(5)
    
    def _database_dynamic_scheduler(self):
        """Database Dynamic 시크릿 갱신 스케줄러"""
        while self.running:
            try:
                print("\n=== Database Dynamic Secret Refresh ===")
                
                secret_result = self.vault_client.get_database_dynamic_secret(
                    self.config['database_dynamic']['role_id']
                )
                
                if secret_result:
                    secret_data = secret_result['data']
                    ttl = secret_result['ttl']
                    
                    print(f"✅ Database Dynamic 시크릿 조회 성공 (TTL: {ttl}초)")
                    print(f"🗄️ Database Dynamic Secret (TTL: {ttl}초):")
                    print(f"  username: {secret_data['username']}")
                    print(f"  password: {secret_data['password']}")
                else:
                    print("❌ Database Dynamic 시크릿 조회 실패")
                
                time.sleep(5)
                
            except Exception as e:
                self.logger.error(f"Database Dynamic 시크릿 갱신 중 오류: {e}")
                time.sleep(5)
    
    def _database_static_scheduler(self):
        """Database Static 시크릿 갱신 스케줄러"""
        while self.running:
            try:
                print("\n=== Database Static Secret Refresh ===")
                
                secret_result = self.vault_client.get_database_static_secret(
                    self.config['database_static']['role_id']
                )
                
                if secret_result:
                    secret_data = secret_result['data']
                    ttl = secret_result['ttl']
                    
                    print(f"✅ Database Static 시크릿 조회 성공 (TTL: {ttl}초)")
                    print(f"🔒 Database Static Secret (TTL: {ttl}초):")
                    print(f"  username: {secret_data['username']}")
                    print(f"  password: {secret_data['password']}")
                else:
                    print("❌ Database Static 시크릿 조회 실패")
                
                time.sleep(10)
                
            except Exception as e:
                self.logger.error(f"Database Static 시크릿 갱신 중 오류: {e}")
                time.sleep(10)


def main():
    """메인 함수"""
    app = VaultApplication()
    app.start()


if __name__ == "__main__":
    main()
