#!/usr/bin/env python3
"""
URL 모니터링 수집 Agent
주기적으로 대상 URL의 상태를 점검하고 백엔드 서버로 전송합니다.
"""

import os
import sys
import time
import logging
import requests
from datetime import datetime, timezone
from typing import Optional, Dict, Any
from dotenv import load_dotenv

# 환경변수 로드
load_dotenv()

# 로깅 설정
LOG_DIR = os.path.join(os.path.dirname(__file__), '..', 'logs')
os.makedirs(LOG_DIR, exist_ok=True)

LOG_FILE = os.path.join(LOG_DIR, 'agent.log')

logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s [%(levelname)s] %(message)s',
    datefmt='%Y-%m-%dT%H:%M:%S',
    handlers=[
        logging.FileHandler(LOG_FILE, encoding='utf-8'),
        logging.StreamHandler(sys.stdout)
    ]
)

logger = logging.getLogger(__name__)


class URLMonitorAgent:
    """URL 모니터링 수집 Agent"""
    
    def __init__(self):
        self.target_url = os.getenv('TARGET_URL')
        self.backend_url = os.getenv('BACKEND_URL', 'http://localhost:8080')
        self.api_key = os.getenv('API_KEY')
        self.interval = int(os.getenv('AGENT_INTERVAL', '60'))
        self.max_retries = 3
        
        # 환경변수 검증
        if not self.target_url:
            raise ValueError("TARGET_URL 환경변수가 설정되지 않았습니다.")
        if not self.api_key:
            raise ValueError("API_KEY 환경변수가 설정되지 않았습니다.")
        
        logger.info(f"Agent 초기화 완료: TARGET_URL={self.target_url}, BACKEND_URL={self.backend_url}, INTERVAL={self.interval}초")
    
    def check_url(self) -> Optional[Dict[str, Any]]:
        """
        대상 URL의 상태를 점검합니다.
        
        Returns:
            수집 결과 딕셔너리 (url, status, latency, timestamp) 또는 None
        """
        try:
            start_time = time.time()
            response = requests.get(
                self.target_url,
                timeout=10,
                allow_redirects=True
            )
            latency_ms = int((time.time() - start_time) * 1000)
            
            timestamp = datetime.now(timezone.utc).isoformat()
            
            result = {
                'url': self.target_url,
                'status': response.status_code,
                'latency': latency_ms,
                'timestamp': timestamp
            }
            
            logger.info(f"URL 점검 완료: Status={response.status_code}, Latency={latency_ms}ms")
            return result
            
        except requests.exceptions.RequestException as e:
            logger.error(f"URL 점검 실패: {str(e)}")
            return None
    
    def send_to_backend(self, data: Dict[str, Any]) -> bool:
        """
        수집 결과를 백엔드 서버로 전송합니다.
        지수형 backoff를 사용하여 최대 3회까지 재시도합니다.
        
        Args:
            data: 전송할 데이터
            
        Returns:
            전송 성공 여부
        """
        headers = {
            'Content-Type': 'application/json',
            'x-api-key': self.api_key
        }
        
        endpoint = f"{self.backend_url}/events"
        
        for attempt in range(1, self.max_retries + 1):
            try:
                response = requests.post(
                    endpoint,
                    json=data,
                    headers=headers,
                    timeout=10
                )
                
                if response.status_code == 201:
                    logger.info(f"POST /events 성공: URL={data['url']}, Status={data['status']}, Latency={data['latency']}ms")
                    return True
                else:
                    logger.warning(f"POST /events 실패 (시도 {attempt}/{self.max_retries}): HTTP {response.status_code}, 응답={response.text}")
                    
            except requests.exceptions.RequestException as e:
                logger.warning(f"POST /events 실패 (시도 {attempt}/{self.max_retries}): {str(e)}")
            
            # 지수형 backoff: 2^attempt 초 대기
            if attempt < self.max_retries:
                wait_time = 2 ** attempt
                logger.info(f"{wait_time}초 후 재시도...")
                time.sleep(wait_time)
        
        logger.error(f"POST /events 최종 실패: 최대 재시도 횟수({self.max_retries}) 초과")
        return False
    
    def run_once(self) -> None:
        """한 번의 점검 및 전송을 수행합니다."""
        logger.info("=" * 60)
        logger.info("URL 점검 시작")
        
        # URL 점검
        result = self.check_url()
        
        if result:
            # 백엔드로 전송
            success = self.send_to_backend(result)
            if success:
                logger.info("점검 및 전송 완료")
            else:
                logger.error("점검 완료, 전송 실패")
        else:
            logger.error("URL 점검 실패로 인해 전송하지 않음")
        
        logger.info("=" * 60)
    
    def run(self) -> None:
        """무한 루프로 주기적으로 점검을 수행합니다."""
        logger.info("Agent 시작: 무한 루프 모드")
        logger.info(f"점검 주기: {self.interval}초")
        
        try:
            while True:
                self.run_once()
                logger.info(f"{self.interval}초 대기 중...")
                time.sleep(self.interval)
                
        except KeyboardInterrupt:
            logger.info("Agent 종료: 사용자 중단")
        except Exception as e:
            logger.error(f"Agent 오류 발생: {str(e)}", exc_info=True)
            raise


def main():
    """메인 함수"""
    try:
        agent = URLMonitorAgent()
        agent.run()
    except ValueError as e:
        logger.error(f"초기화 실패: {str(e)}")
        sys.exit(1)
    except Exception as e:
        logger.error(f"예상치 못한 오류: {str(e)}", exc_info=True)
        sys.exit(1)


if __name__ == '__main__':
    main()