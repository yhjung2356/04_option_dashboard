// WebSocket connection
let stompClient = null;
let autoRefreshEnabled = true;
let lastUpdateTime = null;
let autoUpdateInterval = null; // 1초마다 자동 업데이트

// 전역 페이지 상태 초기화
window.dashboardState = {
    currentView: 'overview',
    dataSource: 'KIS API',
    isConnected: false,
    demoMode: false,
    lastUpdate: null
};

// 페이지 상태 관리 함수들
const StateManager = {
    // 서버에서 현재 상태 가져오기
    async fetchSystemState() {
        try {
            const response = await fetch('/api/market/state');
            const state = await response.json();
            this.updateState(state);
            return state;
        } catch (error) {
            console.error('상태 조회 실패:', error);
            return null;
        }
    },
    
    // 상태 업데이트
    updateState(newState) {
        Object.assign(window.dashboardState, newState);
        console.log('상태 업데이트됨:', window.dashboardState);
        
        // 상태 변경 이벤트 발생
        window.dispatchEvent(new CustomEvent('stateChanged', { 
            detail: window.dashboardState 
        }));
    },
    
    // 현재 상태 가져오기
    getState() {
        return window.dashboardState;
    },
    
    // 특정 상태 값 가져오기
    get(key) {
        return window.dashboardState[key];
    },
    
    // 특정 상태 값 설정
    set(key, value) {
        window.dashboardState[key] = value;
        this.saveToLocalStorage();
    },
    
    // 로컬스토리지에 저장 (새로고침 시에도 유지)
    saveToLocalStorage() {
        try {
            localStorage.setItem('dashboardState', JSON.stringify(window.dashboardState));
        } catch (error) {
            console.error('로컬스토리지 저장 실패:', error);
        }
    },
    
    // 로컬스토리지에서 복원
    restoreFromLocalStorage() {
        try {
            const saved = localStorage.getItem('dashboardState');
            if (saved) {
                const state = JSON.parse(saved);
                Object.assign(window.dashboardState, state);
                console.log('저장된 상태 복원됨:', window.dashboardState);
            }
        } catch (error) {
            console.error('로컬스토리지 복원 실패:', error);
        }
    }
};

// ========================================
// 페이지 스냅샷 및 공유 기능
// ========================================

const PageSnapshot = {
    // 현재 페이지 상태를 JSON으로 추출
    captureState() {
        const snapshot = {
            capturedAt: new Date().toISOString(),
            capturedTime: document.getElementById('current-time')?.textContent || '',
            marketStatus: document.getElementById('status-text')?.textContent || '',
            
            // 시스템 상태
            systemState: {
                dataSource: StateManager.get('dataSource'),
                isConnected: StateManager.get('isConnected'),
                demoMode: StateManager.get('demoMode')
            },
            
            // 선물 데이터
            futures: {
                volume: document.getElementById('futures-volume')?.textContent || '0',
                tradingValue: document.getElementById('futures-value')?.textContent || '0',
                openInterest: document.getElementById('futures-oi')?.textContent || '0'
            },
            
            // 옵션 데이터
            options: {
                volume: document.getElementById('options-volume')?.textContent || '0',
                tradingValue: document.getElementById('options-value')?.textContent || '0',
                openInterest: document.getElementById('options-oi')?.textContent || '0'
            },
            
            // Put/Call Ratio
            putCallRatio: {
                volumeRatio: document.getElementById('pc-ratio-volume')?.textContent || '0.00',
                openInterestRatio: document.getElementById('pc-ratio-oi')?.textContent || '0.00',
                tradingValueRatio: document.getElementById('pc-ratio-value')?.textContent || '0.00'
            },
            
            // 상위 종목 (거래량)
            topByVolume: this.captureTableData('top-by-volume'),
            
            // 상위 종목 (미결제약정)
            topByOpenInterest: this.captureTableData('top-by-oi'),
            
            // 옵션 체인
            optionChain: {
                underlyingPrice: document.getElementById('underlying-price')?.textContent || '0',
                atmStrike: document.getElementById('atm-strike')?.textContent || '0',
                maxPain: document.getElementById('max-pain')?.textContent || '0',
                data: this.captureTableData('option-chain-body', true)
            }
        };
        
        return snapshot;
    },
    
    // 테이블 데이터 추출
    captureTableData(tableId, isOptionChain = false) {
        const tbody = document.getElementById(tableId);
        if (!tbody) return [];
        
        const rows = tbody.querySelectorAll('tr');
        const data = [];
        
        rows.forEach(row => {
            const cells = row.querySelectorAll('td');
            if (cells.length > 0 && !row.textContent.includes('로딩')) {
                const rowData = [];
                cells.forEach(cell => {
                    rowData.push(cell.textContent.trim());
                });
                data.push(rowData);
            }
        });
        
        return data;
    },
    
    // JSON 다운로드
    downloadJSON() {
        const snapshot = this.captureState();
        const dataStr = JSON.stringify(snapshot, null, 2);
        const dataBlob = new Blob([dataStr], { type: 'application/json' });
        
        const url = URL.createObjectURL(dataBlob);
        const link = document.createElement('a');
        link.href = url;
        link.download = `dashboard-snapshot-${this.getTimestamp()}.json`;
        link.click();
        
        URL.revokeObjectURL(url);
        this.showNotification('📄 JSON 파일이 다운로드되었습니다!', 'success');
        console.log('스냅샷 다운로드 완료');
    },
    
    // 텍스트 형식으로 다운로드
    downloadText() {
        const snapshot = this.captureState();
        const text = this.formatAsText(snapshot);
        const dataBlob = new Blob([text], { type: 'text/plain; charset=utf-8' });
        
        const url = URL.createObjectURL(dataBlob);
        const link = document.createElement('a');
        link.href = url;
        link.download = `dashboard-report-${this.getTimestamp()}.txt`;
        link.click();
        
        URL.revokeObjectURL(url);
        this.showNotification('💾 텍스트 리포트가 다운로드되었습니다!', 'success');
        console.log('텍스트 리포트 다운로드 완료');
    },
    
    // 텍스트 형식으로 포맷팅
    formatAsText(snapshot) {
        let text = '╔════════════════════════════════════════════════════════════════╗\n';
        text += '║         선물/옵션 실시간 거래 대시보드 스냅샷                 ║\n';
        text += '╚════════════════════════════════════════════════════════════════╝\n\n';
        
        text += `📅 캡처 시간: ${snapshot.capturedTime}\n`;
        text += `📊 시장 상태: ${snapshot.marketStatus}\n`;
        text += `💾 데이터 소스: ${snapshot.systemState.dataSource}\n`;
        text += `🔌 연결 상태: ${snapshot.systemState.isConnected ? '✅ 연결됨' : '❌ 연결 안됨'}\n\n`;
        
        text += '━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n';
        text += '🚀 선물 전체\n';
        text += '━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n';
        text += `   거래량:     ${snapshot.futures.volume}\n`;
        text += `   거래대금:   ${snapshot.futures.tradingValue}\n`;
        text += `   미결제약정: ${snapshot.futures.openInterest}\n\n`;
        
        text += '━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n';
        text += '📊 옵션 전체\n';
        text += '━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n';
        text += `   거래량:     ${snapshot.options.volume}\n`;
        text += `   거래대금:   ${snapshot.options.tradingValue}\n`;
        text += `   미결제약정: ${snapshot.options.openInterest}\n\n`;
        
        text += '━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n';
        text += '⚖️  Put/Call Ratio\n';
        text += '━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n';
        text += `   거래량 Ratio:   ${snapshot.putCallRatio.volumeRatio}\n`;
        text += `   미결제 Ratio:   ${snapshot.putCallRatio.openInterestRatio}\n`;
        text += `   거래대금 Ratio: ${snapshot.putCallRatio.tradingValueRatio}\n\n`;
        
        text += '━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n';
        text += '📈 거래량 TOP 5\n';
        text += '━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n';
        if (snapshot.topByVolume && snapshot.topByVolume.length > 0) {
            snapshot.topByVolume.slice(0, 5).forEach((row, idx) => {
                text += `${(idx + 1).toString().padStart(2, ' ')}. ${row.join(' | ')}\n`;
            });
        } else {
            text += '   데이터 없음\n';
        }
        
        text += '\n━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n';
        text += '🔥 미결제약정 TOP 5\n';
        text += '━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n';
        if (snapshot.topByOpenInterest && snapshot.topByOpenInterest.length > 0) {
            snapshot.topByOpenInterest.slice(0, 5).forEach((row, idx) => {
                text += `${(idx + 1).toString().padStart(2, ' ')}. ${row.join(' | ')}\n`;
            });
        } else {
            text += '   데이터 없음\n';
        }
        
        text += '\n━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n';
        text += '📋 옵션 체인 정보\n';
        text += '━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n';
        text += `   기초자산:   ${snapshot.optionChain.underlyingPrice}\n`;
        text += `   ATM 행사가: ${snapshot.optionChain.atmStrike}\n`;
        text += `   Max Pain:   ${snapshot.optionChain.maxPain}\n`;
        
        text += '\n' + '═'.repeat(64) + '\n';
        text += `생성 일시: ${new Date().toLocaleString('ko-KR')}\n`;
        text += '═'.repeat(64) + '\n';
        
        return text;
    },
    
    // 클립보드에 복사
    async copyToClipboard() {
        const snapshot = this.captureState();
        const text = this.formatAsText(snapshot);
        
        try {
            await navigator.clipboard.writeText(text);
            this.showNotification('✅ 페이지 상태가 클립보드에 복사되었습니다!\n\n어디든 붙여넣기(Ctrl+V) 하실 수 있습니다.', 'success');
            console.log('클립보드 복사 완료');
        } catch (err) {
            console.error('클립보드 복사 실패:', err);
            // 폴백: textarea 사용
            this.copyToClipboardFallback(text);
        }
    },
    
    // 클립보드 복사 폴백 메서드
    copyToClipboardFallback(text) {
        const textarea = document.createElement('textarea');
        textarea.value = text;
        textarea.style.position = 'fixed';
        textarea.style.opacity = '0';
        document.body.appendChild(textarea);
        textarea.select();
        
        try {
            document.execCommand('copy');
            this.showNotification('✅ 페이지 상태가 클립보드에 복사되었습니다!\n\n어디든 붙여넣기(Ctrl+V) 하실 수 있습니다.', 'success');
            console.log('클립보드 복사 완료 (폴백)');
        } catch (err) {
            console.error('클립보드 복사 실패 (폴백):', err);
            this.showNotification('❌ 클립보드 복사에 실패했습니다.', 'error');
        }
        
        document.body.removeChild(textarea);
    },
    
    // 알림 표시
    showNotification(message, type = 'info') {
        // 간단한 알림 (기존 alert 대체)
        const notification = document.createElement('div');
        notification.className = `snapshot-notification ${type}`;
        notification.textContent = message;
        notification.style.cssText = `
            position: fixed;
            top: 80px;
            right: 20px;
            background: ${type === 'success' ? '#4CAF50' : type === 'error' ? '#f44336' : '#2196F3'};
            color: white;
            padding: 16px 24px;
            border-radius: 8px;
            box-shadow: 0 4px 12px rgba(0,0,0,0.15);
            z-index: 10000;
            font-size: 14px;
            max-width: 400px;
            animation: slideInRight 0.3s ease;
        `;
        
        document.body.appendChild(notification);
        
        // 3초 후 자동 제거
        setTimeout(() => {
            notification.style.animation = 'slideOutRight 0.3s ease';
            setTimeout(() => {
                document.body.removeChild(notification);
            }, 300);
        }, 3000);
    },
    
    // 콘솔에 출력
    printToConsole() {
        const snapshot = this.captureState();
        console.log('========================================');
        console.log('대시보드 현재 상태');
        console.log('========================================');
        console.log(snapshot);
        console.log('========================================');
        console.log('텍스트 형식:');
        console.log(this.formatAsText(snapshot));
        console.log('========================================');
    },
    
    // 타임스탬프 생성
    getTimestamp() {
        const now = new Date();
        return now.toISOString().replace(/[:.]/g, '-').slice(0, 19);
    },
    
    // HTML 스냅샷 생성 (전체 페이지 HTML)
    captureHTML() {
        const html = document.documentElement.outerHTML;
        const dataBlob = new Blob([html], { type: 'text/html; charset=utf-8' });
        
        const url = URL.createObjectURL(dataBlob);
        const link = document.createElement('a');
        link.href = url;
        link.download = `dashboard-page-${this.getTimestamp()}.html`;
        link.click();
        
        URL.revokeObjectURL(url);
        console.log('HTML 스냅샷 다운로드 완료');
    }
};

// 전역에서 접근 가능하도록
window.PageSnapshot = PageSnapshot;

// 단축키 등록 (Ctrl+Shift+S: 텍스트 저장, Ctrl+Shift+C: 클립보드 복사)
document.addEventListener('keydown', function(e) {
    if (e.ctrlKey && e.shiftKey) {
        if (e.key === 'S') {
            e.preventDefault();
            PageSnapshot.downloadText();
        } else if (e.key === 'C') {
            e.preventDefault();
            PageSnapshot.copyToClipboard();
        } else if (e.key === 'J') {
            e.preventDefault();
            PageSnapshot.downloadJSON();
        } else if (e.key === 'H') {
            e.preventDefault();
            PageSnapshot.captureHTML();
        }
    }
});

// Connect to WebSocket
function connect() {
    updateConnectionStatus('연결 중...', false);
    
    const socket = new SockJS('/ws');
    stompClient = Stomp.over(socket);
    
    stompClient.connect({}, function(frame) {
        console.log('WebSocket 연결됨');
        StateManager.set('isConnected', true);
        StateManager.set('lastUpdate', new Date().toISOString());
        updateConnectionStatus('연결됨', true);
        
        // Subscribe to market overview updates
        stompClient.subscribe('/topic/market-overview', function(message) {
            if (autoRefreshEnabled) {
                const data = JSON.parse(message.body);
                updateMarketOverview(data);
                updateLastUpdateTime();
            }
        });
        
        // ✨ 실시간 선물 체결가 구독
        stompClient.subscribe('/topic/futures/realtime', function(message) {
            const data = JSON.parse(message.body);
            updateFuturesRealtimePrice(data);
        });
        
        // ✨ 실시간 선물 호가 구독
        stompClient.subscribe('/topic/futures/quote', function(message) {
            const data = JSON.parse(message.body);
            updateFuturesRealtimeQuote(data);
        });
        
        // ✨ 실시간 옵션 체결가 구독
        stompClient.subscribe('/topic/options/realtime', function(message) {
            const data = JSON.parse(message.body);
            updateOptionsRealtimePrice(data);
        });
        
        // ✨ 실시간 옵션 호가 구독
        stompClient.subscribe('/topic/options/quote', function(message) {
            const data = JSON.parse(message.body);
            updateOptionsRealtimeQuote(data);
        });
        
        // 1초마다 자동 업데이트 시작
        startAutoUpdate();
    }, function(error) {
        console.error('WebSocket 연결 실패:', error);
        updateConnectionStatus('연결 실패', false);
        StateManager.set('isConnected', false);
        
        // 자동 업데이트 중지
        stopAutoUpdate();
        
        // 5초 후 재연결 시도
        setTimeout(connect, 5000);
    });
}

// 1초마다 자동 업데이트 시작
function startAutoUpdate() {
    // 기존 interval 정리
    if (autoUpdateInterval) {
        clearInterval(autoUpdateInterval);
    }
    
    // 1초(1000ms)마다 데이터 업데이트
    autoUpdateInterval = setInterval(function() {
        if (autoRefreshEnabled) {
            const currentView = window.dashboardState?.currentView || 'overview';
            
            // 마켓 오버뷰 업데이트 (모든 뷰에서 필요)
            fetch('/api/market/overview')
                .then(response => response.json())
                .then(data => {
                    updateMarketOverview(data);
                    updateLastUpdateTime();
                })
                .catch(error => console.error('자동 업데이트 실패:', error));
            
            // 옵션 체인 업데이트 (overview, chain 뷰에서 필요)
            if (currentView === 'overview' || currentView === 'chain') {
                fetch('/api/market/option-chain')
                    .then(response => response.json())
                    .then(data => {
                        updateOptionChainData(data);
                    })
                    .catch(error => console.error('옵션 체인 업데이트 실패:', error));
            }
            
            // 선물 데이터 업데이트 (futures 뷰)
            if (currentView === 'futures') {
                fetch('/api/market/futures')
                    .then(response => response.json())
                    .then(data => {
                        const container = document.querySelector('.futures-with-orderbook');
                        if (container) {
                            updateFuturesTable(data);
                        }
                    })
                    .catch(error => console.error('선물 업데이트 실패:', error));
            }
            
            // 옵션 데이터 업데이트 (options 뷰)
            if (currentView === 'options') {
                fetch('/api/market/options')
                    .then(response => response.json())
                    .then(data => {
                        const container = document.querySelector('.options-view-content');
                        if (container) {
                            updateOptionsTable(data);
                        }
                    })
                    .catch(error => console.error('옵션 업데이트 실패:', error));
            }
        }
    }, 1000); // 1초
    
    console.log('✅ 1초 자동 업데이트 시작 (모든 뷰 지원)');
}

// 자동 업데이트 중지
function stopAutoUpdate() {
    if (autoUpdateInterval) {
        clearInterval(autoUpdateInterval);
        autoUpdateInterval = null;
        console.log('⏸️ 자동 업데이트 중지');
    }
}

// Update market overview
function updateMarketOverview(data) {
    console.log('📊 updateMarketOverview 호출됨:', data);
    
    // Update futures data
    const futuresVolume = document.getElementById('futures-volume');
    const futuresValue = document.getElementById('futures-value');
    const futuresOi = document.getElementById('futures-oi');
    
    console.log('🔍 DOM 요소 체크:');
    console.log('  futures-volume:', futuresVolume ? '✅ 존재' : '❌ 없음');
    console.log('  futures-value:', futuresValue ? '✅ 존재' : '❌ 없음');
    console.log('  futures-oi:', futuresOi ? '✅ 존재' : '❌ 없음');
    
    if (futuresVolume) {
        const formatted = formatNumber(data.totalFuturesVolume);
        console.log('  선물 거래량 업데이트:', data.totalFuturesVolume, '->', formatted);
        futuresVolume.textContent = formatted;
    }
    if (futuresValue) {
        const formatted = formatCurrency(data.totalFuturesTradingValue);
        console.log('  선물 거래대금 업데이트:', data.totalFuturesTradingValue, '->', formatted);
        futuresValue.textContent = formatted;
    }
    if (futuresOi) {
        const formatted = formatNumber(data.totalFuturesOpenInterest);
        console.log('  선물 미결제 업데이트:', data.totalFuturesOpenInterest, '->', formatted);
        futuresOi.textContent = formatted;
    }
    
    // Update options data
    const optionsVolume = document.getElementById('options-volume');
    const optionsValue = document.getElementById('options-value');
    const optionsOi = document.getElementById('options-oi');
    
    console.log('  options-volume:', optionsVolume ? '✅ 존재' : '❌ 없음');
    console.log('  options-value:', optionsValue ? '✅ 존재' : '❌ 없음');
    console.log('  options-oi:', optionsOi ? '✅ 존재' : '❌ 없음');
    
    if (optionsVolume) {
        const formatted = formatNumber(data.totalOptionsVolume);
        console.log('  옵션 거래량 업데이트:', data.totalOptionsVolume, '->', formatted);
        optionsVolume.textContent = formatted;
    }
    if (optionsValue) {
        const formatted = formatCurrency(data.totalOptionsTradingValue);
        console.log('  옵션 거래대금 업데이트:', data.totalOptionsTradingValue, '->', formatted);
        optionsValue.textContent = formatted;
    }
    if (optionsOi) {
        const formatted = formatNumber(data.totalOptionsOpenInterest);
        console.log('  옵션 미결제 업데이트:', data.totalOptionsOpenInterest, '->', formatted);
        optionsOi.textContent = formatted;
    }
    
    console.log('✅ 데이터 업데이트 완료 - 선물:', data.totalFuturesVolume, '옵션:', data.totalOptionsVolume);
    
    // Update Put/Call Ratio
    if (data.putCallRatio) {
        document.getElementById('pc-ratio-volume').textContent = data.putCallRatio.volumeRatio.toFixed(2);
        document.getElementById('pc-ratio-oi').textContent = data.putCallRatio.openInterestRatio.toFixed(2);
        document.getElementById('pc-ratio-value').textContent = data.putCallRatio.tradingValueRatio.toFixed(2);
        
        // Color coding based on ratio
        updateRatioColor('pc-ratio-volume', data.putCallRatio.volumeRatio);
        updateRatioColor('pc-ratio-oi', data.putCallRatio.openInterestRatio);
        updateRatioColor('pc-ratio-value', data.putCallRatio.tradingValueRatio);
        
        // Update market sentiment based on P/C ratio
        updateMarketSentiment(data.putCallRatio);
    }
    
    // Update top traded
    if (data.topByVolume) {
        updateTopTradedTable('top-by-volume', data.topByVolume);
    }
    
    if (data.topByOpenInterest) {
        updateTopTradedTable('top-by-oi', data.topByOpenInterest);
    }
}

// Update market sentiment gauge
function updateMarketSentiment(putCallRatio) {
    const avgRatio = (putCallRatio.volumeRatio + putCallRatio.openInterestRatio) / 2;
    
    // Calculate sentiment (0-100)
    // P/C < 0.7: Bullish (70-100)
    // P/C 0.7-1.0: Neutral-Bullish (50-70)
    // P/C 1.0-1.3: Neutral-Bearish (30-50)
    // P/C > 1.3: Bearish (0-30)
    let sentimentValue = 50;
    let sentimentLabel = '중립';
    
    if (avgRatio < 0.7) {
        sentimentValue = 70 + (0.7 - avgRatio) * 50;
        sentimentLabel = '강세';
    } else if (avgRatio < 1.0) {
        sentimentValue = 50 + (1.0 - avgRatio) * 66.7;
        sentimentLabel = '약강세';
    } else if (avgRatio < 1.3) {
        sentimentValue = 30 + (1.3 - avgRatio) * 66.7;
        sentimentLabel = '약약세';
    } else {
        sentimentValue = Math.max(0, 30 - (avgRatio - 1.3) * 30);
        sentimentLabel = '약세';
    }
    
    const fillElement = document.getElementById('sentiment-fill');
    const labelElement = document.getElementById('sentiment-label');
    
    if (fillElement) {
        fillElement.style.width = sentimentValue + '%';
    }
    
    if (labelElement) {
        labelElement.textContent = sentimentLabel;
    }
}

// Update ratio color based on value
function updateRatioColor(elementId, ratio) {
    const element = document.getElementById(elementId);
    element.classList.remove('price-up', 'price-down');
    
    if (ratio > 1) {
        element.classList.add('price-down'); // Bearish
    } else if (ratio < 0.7) {
        element.classList.add('price-up'); // Bullish
    }
}

// Update top traded table
function updateTopTradedTable(tableId, data) {
    const tbody = document.getElementById(tableId);
    
    if (!data || data.length === 0) {
        tbody.innerHTML = '<tr><td colspan="7" class="loading">데이터 없음</td></tr>';
        return;
    }
    
    let html = '';
    data.forEach((item, index) => {
        const typeClass = item.type === 'FUTURES' ? 'futures-type' : 'options-type';
        const changeClass = item.changePercent > 0 ? 'price-up' : item.changePercent < 0 ? 'price-down' : '';
        
        html += `
            <tr>
                <td>${index + 1}</td>
                <td class="${typeClass}">${item.symbol}</td>
                <td>${item.name}</td>
                <td class="formatted-number">${formatPrice(item.currentPrice)}</td>
                <td class="formatted-number">${formatNumber(item.volume)}</td>
                <td class="formatted-number">${formatCurrency(item.tradingValue)}</td>
                <td class="formatted-number">${formatNumber(item.openInterest)}</td>
            </tr>
        `;
    });
    
    tbody.innerHTML = html;
}

// Fetch and update option chain
function updateOptionChain() {
    fetch('/api/market/option-chain')
        .then(response => response.json())
        .then(data => {
            updateOptionChainData(data);
        })
        .catch(error => console.error('Error fetching option chain:', error));
}

// Format bid/ask with volume
function formatBidAsk(bid, ask) {
    if (!bid || !ask) return '-';
    return `${formatPrice(bid)}/${formatPrice(ask)}`;
}

// Format bid/ask with volume in parentheses
function formatBidAskWithVolume(price, volume) {
    if (!price) return '-';
    const priceStr = formatPrice(price);
    const volStr = volume ? `<span class="quote-volume">(${formatNumber(volume)})</span>` : '';
    return `${priceStr}${volStr}`;
}

// Format OI with change
function formatOIWithChange(oi, change) {
    const oiStr = formatNumber(oi);
    if (!change || change === 0) return oiStr;
    const changeClass = change > 0 ? 'positive' : 'negative';
    const changeStr = change > 0 ? `+${formatNumber(change)}` : formatNumber(change);
    return `${oiStr}<br><span class="oi-change ${changeClass}">${changeStr}</span>`;
}

// Format Greeks value
function formatGreeks(value) {
    if (!value && value !== 0) return '-';
    return parseFloat(value).toFixed(2);
}

// Update current time
function updateTime() {
    const now = new Date();
    const timeString = now.toLocaleString('ko-KR', {
        year: 'numeric',
        month: '2-digit',
        day: '2-digit',
        hour: '2-digit',
        minute: '2-digit',
        second: '2-digit',
        hour12: false
    });
    document.getElementById('current-time').textContent = timeString;
    
    // 장 시간 체크
    updateMarketStatus(now);
}

// 장 시간 체크 함수
async function updateMarketStatus(now) {
    const day = now.getDay(); // 0=일요일, 6=토요일
    const hours = now.getHours();
    const minutes = now.getMinutes();
    const time = hours * 100 + minutes;
    
    const statusElement = document.getElementById('market-status');
    const statusText = document.getElementById('status-text');
    const statusIcon = statusElement.querySelector('i');
    const closedBanner = document.getElementById('market-closed-banner');
    
    // 서버에서 거래일 여부 확인
    const state = StateManager.getState();
    const isTradingDay = state.isTradingDay;
    const isHoliday = state.isHoliday;
    
    // 휴장일(공휴일) 체크 - 최우선
    if (isHoliday === true || isTradingDay === false) {
        setMarketClosed(statusElement, statusIcon, statusText, '휴장', closedBanner);
        return;
    }
    
    // 주말 체크 - "장 마감"으로 통일
    if (day === 0 || day === 6) {
        setMarketClosed(statusElement, statusIcon, statusText, '장 마감', closedBanner);
        return;
    }
    
    // 주간장: 09:00 ~ 15:45
    const isDaySession = time >= 900 && time < 1545;
    
    // 야간장: 18:00 ~ 익일 05:00
    const isNightSession = time >= 1800 || time < 500;
    
    if (isDaySession) {
        setMarketOpen(statusElement, statusIcon, statusText, '주간장 거래중', closedBanner);
    } else if (isNightSession) {
        setMarketOpen(statusElement, statusIcon, statusText, '야간장 거래중', closedBanner);
    } else {
        setMarketClosed(statusElement, statusIcon, statusText, '장 마감', closedBanner);
    }
}

function setMarketOpen(statusElement, statusIcon, statusText, message, closedBanner) {
    statusElement.classList.remove('market-closed-indicator');
    statusIcon.classList.remove('status-closed');
    statusIcon.classList.add('status-live');
    statusText.textContent = message;
    if (closedBanner) closedBanner.style.display = 'none';
}

function setMarketClosed(statusElement, statusIcon, statusText, message, closedBanner) {
    statusElement.classList.add('market-closed-indicator');
    statusIcon.classList.remove('status-live');
    statusIcon.classList.add('status-closed');
    statusText.textContent = message;
    if (closedBanner) closedBanner.style.display = 'flex';
}

// Format number with commas
function formatNumber(num) {
    if (!num) return '0';
    return num.toLocaleString('ko-KR');
}

// Format currency
function formatCurrency(num) {
    if (!num) return '0원';
    
    if (num >= 100000000) {
        return (num / 100000000).toFixed(1) + '억원';
    } else if (num >= 10000) {
        return (num / 10000).toFixed(0) + '만원';
    }
    
    return num.toLocaleString('ko-KR') + '원';
}

// Format price
function formatPrice(price) {
    if (!price) return '-';
    return parseFloat(price).toFixed(2);
}

// Load initial data from REST API
async function loadInitialData() {
    console.log('Loading initial data from REST API...');
    
    try {
        // Load market overview
        const overviewResponse = await fetch('/api/market/overview');
        if (overviewResponse.ok) {
            const overviewData = await overviewResponse.json();
            console.log('Market overview data loaded:', overviewData);
            updateMarketOverview(overviewData);
        } else {
            console.error('Failed to load market overview:', overviewResponse.status);
        }
        
        // Load option chain
        const optionChainResponse = await fetch('/api/market/option-chain');
        if (optionChainResponse.ok) {
            const optionChainData = await optionChainResponse.json();
            console.log('Option chain data loaded:', optionChainData);
            console.log('🔍 atmGreeks in response:', optionChainData.atmGreeks);
            updateOptionChainData(optionChainData);
        } else {
            console.error('Failed to load option chain:', optionChainResponse.status);
        }
        
        console.log('Initial data loaded successfully');
    } catch (error) {
        console.error('Error loading initial data:', error);
    }
}

// Update option chain data (extracted from updateOptionChain for reuse)
function updateOptionChainData(data) {
    // Update option chain info
    document.getElementById('underlying-price').textContent = formatPrice(data.underlyingPrice);
    document.getElementById('atm-strike').textContent = formatPrice(data.atmStrike);
    document.getElementById('max-pain').textContent = formatPrice(data.maxPainPrice);
    
    // Update ATM strike price in option page header
    const atmStrikePriceElement = document.getElementById('atm-strike-price');
    if (atmStrikePriceElement) {
        atmStrikePriceElement.textContent = formatPrice(data.atmStrike);
    }
    
    // Update Greeks (ATM 기준)
    updateGreeksDisplay(data.strikeChain, data.atmStrike);
    
    // Update option chain table
    const tbody = document.getElementById('option-chain-body');
    
    if (!data.strikeChain || data.strikeChain.length === 0) {
        tbody.innerHTML = '<tr><td colspan="11" class="loading">데이터 없음</td></tr>';
        return;
    }
    
    let html = '';
    const maxVolume = Math.max(...data.strikeChain.map(s => s.totalVolume));
    const maxOI = Math.max(...data.strikeChain.map(s => s.totalOpenInterest));
    
    // ATM 기준 위아래 5개씩만 표시 (총 10개)
    const atmIndex = data.strikeChain.findIndex(s => s.strikePrice == data.atmStrike);
    const displayRange = 5; // ATM 위아래로 각각 5개
    const startIndex = Math.max(0, atmIndex - displayRange);
    const endIndex = Math.min(data.strikeChain.length, atmIndex + displayRange + 1);
    const filteredStrikes = data.strikeChain.slice(startIndex, endIndex);
    
    console.log(`Displaying ${filteredStrikes.length} strikes (ATM index: ${atmIndex}, range: ${startIndex}-${endIndex})`);
    
    filteredStrikes.forEach(strike => {
        const isATM = strike.strikePrice == data.atmStrike;
        const rowClass = isATM ? 'atm-row' : '';
        
        const callVolumeClass = strike.callVolume >= maxVolume * 0.7 ? 'high-volume' : '';
        const putVolumeClass = strike.putVolume >= maxVolume * 0.7 ? 'high-volume' : '';
        const callOIClass = strike.callOpenInterest >= maxOI * 0.7 ? 'high-oi' : '';
        const putOIClass = strike.putOpenInterest >= maxOI * 0.7 ? 'high-oi' : '';
        
        html += `
            <tr class="${rowClass}">
                <td class="call-cell">${formatBidAskWithVolume(strike.callAskPrice, strike.callAskVolume)}</td>
                <td class="call-cell">${formatBidAskWithVolume(strike.callBidPrice, strike.callBidVolume)}</td>
                <td class="call-cell ${callVolumeClass}">${formatNumber(strike.callVolume)}</td>
                <td class="call-cell ${callOIClass}">${formatOIWithChange(strike.callOpenInterest, strike.callOIChange)}</td>
                <td class="call-cell formatted-number">${formatPrice(strike.callPrice)}</td>
                <td class="call-cell greeks-cell">${formatGreeks(strike.callTheoretical)}</td>
                <td class="call-cell greeks-cell">${formatGreeks(strike.callIntrinsic)}</td>
                <td class="call-cell greeks-cell">${formatGreeks(strike.callTimeValue)}</td>
                <td class="strike-cell">${formatPrice(strike.strikePrice)}</td>
                <td class="put-cell greeks-cell">${formatGreeks(strike.putTimeValue)}</td>
                <td class="put-cell greeks-cell">${formatGreeks(strike.putIntrinsic)}</td>
                <td class="put-cell greeks-cell">${formatGreeks(strike.putTheoretical)}</td>
                <td class="put-cell formatted-number">${formatPrice(strike.putPrice)}</td>
                <td class="put-cell ${putOIClass}">${formatOIWithChange(strike.putOpenInterest, strike.putOIChange)}</td>
                <td class="put-cell ${putVolumeClass}">${formatNumber(strike.putVolume)}</td>
                <td class="put-cell">${formatBidAskWithVolume(strike.putAskPrice, strike.putAskVolume)}</td>
                <td class="put-cell">${formatBidAskWithVolume(strike.putBidPrice, strike.putBidVolume)}</td>
            </tr>
        `;
    });
    
    tbody.innerHTML = html;
}

// Update Greeks display
function updateGreeksDisplay(strikeChain, atmStrike) {
    if (!strikeChain || strikeChain.length === 0) {
        console.log('❌ No strike chain data available for Greeks');
        return;
    }
    
    // Find ATM strike
    const atmData = strikeChain.find(s => s.strikePrice == atmStrike);
    if (!atmData) {
        console.log('❌ ATM strike not found:', atmStrike);
        return;
    }
    
    console.log('✅ ATM Data for Greeks:', atmData);
    console.log('📊 Greeks Values:', {
        callDelta: atmData.callDelta,
        putDelta: atmData.putDelta,
        callGamma: atmData.callGamma,
        callTheta: atmData.callTheta,
        callVega: atmData.callVega,
        callIV: atmData.callImpliedVolatility
    });
    
    // Update Delta
    const deltaCall = document.getElementById('delta-call');
    const deltaPut = document.getElementById('delta-put');
    if (deltaCall && deltaPut) {
        deltaCall.textContent = atmData.callDelta ? atmData.callDelta.toFixed(3) : '--';
        deltaPut.textContent = atmData.putDelta ? atmData.putDelta.toFixed(3) : '--';
    }
    
    // Update other Greeks (using call values as representative)
    const gammaElement = document.getElementById('greek-gamma');
    const thetaElement = document.getElementById('greek-theta');
    const vegaElement = document.getElementById('greek-vega');
    const ivElement = document.getElementById('greek-iv');
    
    if (gammaElement) {
        gammaElement.textContent = atmData.callGamma ? atmData.callGamma.toFixed(4) : '--';
    }
    
    if (thetaElement) {
        thetaElement.textContent = atmData.callTheta ? atmData.callTheta.toFixed(4) : '--';
    }
    
    if (vegaElement) {
        vegaElement.textContent = atmData.callVega ? atmData.callVega.toFixed(4) : '--';
    }
    
    if (ivElement) {
        const iv = atmData.callImpliedVolatility;
        if (iv) {
            // IV는 이미 백분율 값 (예: 23.88)
            const ivPercent = iv.toFixed(2) + '%';
            ivElement.textContent = ivPercent;
            console.log('IV Updated:', ivPercent);
            
            // Update IV index in sentiment card
            const ivIndexElement = document.getElementById('iv-index');
            if (ivIndexElement) {
                const ivValue = iv.toFixed(1);
                ivIndexElement.textContent = ivValue;
                console.log('IV Index Updated:', ivValue);
            }
        } else {
            ivElement.textContent = '--';
            console.warn('IV data not available');
            const ivIndexElement = document.getElementById('iv-index');
            if (ivIndexElement) {
                ivIndexElement.textContent = '--';
            }
        }
    }
}

// Initialize dashboard
document.addEventListener('DOMContentLoaded', async function() {
    console.log('Dashboard initializing...');
    
    // 저장된 상태 복원 (선택사항)
    StateManager.restoreFromLocalStorage();
    
    // 서버에서 최신 상태 가져오기 (선택사항)
    await StateManager.fetchSystemState();
    
    // 상태 변경 이벤트 리스너 등록 예제
    window.addEventListener('stateChanged', function(event) {
        console.log('상태가 변경되었습니다:', event.detail);
        
        // 데이터소스에 따라 UI 업데이트
        if (event.detail.dataSource) {
            updateDataSourceIndicator(event.detail.dataSource);
        }
    });
    
    // Load initial data from REST API first
    await loadInitialData();
    
    // Connect to WebSocket
    connect();
    
    // Update time every second
    updateTime();
    setInterval(updateTime, 1000);
    
    // Update option chain every 1 second for real-time data
    updateOptionChain();
    setInterval(updateOptionChain, 1000);
    
    console.log('Dashboard initialized successfully');
    console.log('현재 페이지 상태:', StateManager.getState());
});

// 데이터 소스 표시 업데이트 (예제)
function updateDataSourceIndicator(dataSource) {
    const indicator = document.querySelector('.data-source-indicator');
    if (indicator) {
        indicator.textContent = `데이터 소스: ${dataSource}`;
    }
}

// 사이드바 뷰 전환
function switchView(event, view) {
    event.preventDefault();
    console.log('🔄 뷰 전환 시도:', view);
    
    // 현재 활성 링크 제거
    document.querySelectorAll('.sidebar-link').forEach(link => {
        link.classList.remove('active');
    });
    
    // 클릭한 링크 활성화
    event.currentTarget.classList.add('active');
    
    // 상태 업데이트
    window.dashboardState.currentView = view;
    
    // main content 영역 가져오기
    const mainContent = document.querySelector('.dashboard-content');
    if (!mainContent) {
        console.error('🚨 .dashboard-content 요소를 찾을 수 없습니다!');
        return;
    }
    
    // 뷰에 따라 컨텐츠 표시
    switch(view) {
        case 'overview':
            showOverviewView(mainContent);
            break;
        case 'futures':
            showFuturesView(mainContent);
            break;
        case 'options':
            showOptionsView(mainContent);
            break;
        case 'greeks':
            showGreeksView(mainContent);
            break;
        case 'chain':
            showChainView(mainContent);
            break;
        default:
            console.warn('알 수 없는 뷰:', view);
    }
    
    console.log('✅ 뷰 전환 완료:', view);
}

// 대시보드 뷰 표시
function showOverviewView(container) {
    console.log('🏠 Overview 뷰로 복귀');
    
    // 동적으로 추가된 컨텐츠 제거
    const dynamicContent = container.querySelector('.dynamic-view-content');
    if (dynamicContent) {
        dynamicContent.remove();
        console.log('  ✓ 동적 컨텐츠 제거됨');
    }
    
    // 모든 원래 섹션 표시
    container.querySelectorAll('section').forEach(section => {
        section.style.display = '';
    });
    
    // 시장 데이터 다시 로드
    fetch('/api/market/overview')
        .then(response => response.json())
        .then(data => {
            updateMarketOverview(data);
            console.log('  ✓ 마켓 오버뷰 갱신됨');
        })
        .catch(error => console.error('마켓 오버뷰 로드 실패:', error));
    
    // 옵션 체인 데이터도 다시 로드
    fetch('/api/market/option-chain')
        .then(response => response.json())
        .then(data => {
            updateOptionChainData(data);
            console.log('  ✓ 옵션 체인 갱신됨');
        })
        .catch(error => console.error('옵션 체인 로드 실패:', error));
    
    console.log('✅ Overview 뷰 표시 완료');
}

// 선물 뷰 표시 (호가창 통합)
function showFuturesView(container) {
    // 모든 섹션 숨기기
    container.querySelectorAll('section').forEach(section => {
        section.style.display = 'none';
    });
    
    // 선물 데이터 로드 및 표시
    $.ajax({
        url: '/api/market/futures',
        method: 'GET',
        success: function(data) {
            console.log('✅ 선물 데이터 로드:', data);
            renderFuturesDataWithOrderbook(container, data);
        },
        error: function(error) {
            console.error('❌ 선물 데이터 로드 실패:', error);
            container.innerHTML = '<div style="padding: 40px; text-align: center; color: white;"><h2>선물 데이터를 불러올 수 없습니다.</h2></div>';
        }
    });
}

// 옵션 뷰 표시
function showOptionsView(container) {
    // 모든 섹션 숨기기
    container.querySelectorAll('section').forEach(section => {
        section.style.display = 'none';
    });
    
    // 옵션 데이터 로드 및 표시
    $.ajax({
        url: '/api/market/options',
        method: 'GET',
        success: function(data) {
            console.log('✅ 옵션 데이터 로드:', data);
            renderOptionsData(container, data);
        },
        error: function(error) {
            console.error('❌ 옵션 데이터 로드 실패:', error);
            container.innerHTML = '<div style="padding: 40px; text-align: center; color: white;"><h2>옵션 데이터를 불러올 수 없습니다.</h2></div>';
        }
    });
}

// Greeks 뷰 표시
function showGreeksView(container) {
    // 모든 섹션 숨기기
    container.querySelectorAll('section').forEach(section => {
        section.style.display = 'none';
    });
    
    // Greeks 섹션을 동적으로 생성하여 전체 화면 표시
    let html = `
        <section class="greeks-section dynamic-view-content" style="display: block; margin: 0; flex: 1;">
            <div class="card greeks-card" style="height: 100%;">
                <div class="card-header-compact">
                    <i class="fas fa-calculator"></i> Greeks 요약 (ATM 기준)
                </div>
                <div class="greeks-body">
                    <div class="greek-item">
                        <span class="greek-label">
                            Delta
                            <i class="fas fa-info-circle greek-info" data-tooltip="기초자산 가격이 1원 변할 때 옵션 가격의 변화량. Call은 0~1, Put은 -1~0 범위"></i>
                        </span>
                        <span class="greek-value" id="greek-delta">
                            <span class="greek-call" id="delta-call">--</span> / 
                            <span class="greek-put" id="delta-put">--</span>
                        </span>
                    </div>
                    <div class="greek-item">
                        <span class="greek-label">
                            Gamma
                            <i class="fas fa-info-circle greek-info" data-tooltip="기초자산 가격 변화에 따른 Delta의 변화율. 높을수록 Delta 변동성이 큼"></i>
                        </span>
                        <span class="greek-value" id="greek-gamma">--</span>
                    </div>
                    <div class="greek-item">
                        <span class="greek-label">
                            Theta
                            <i class="fas fa-info-circle greek-info" data-tooltip="시간 경과에 따른 옵션 가격의 하락률. 보통 음수 값으로 시간 가치 소멸을 의미"></i>
                        </span>
                        <span class="greek-value" id="greek-theta">--</span>
                    </div>
                    <div class="greek-item">
                        <span class="greek-label">
                            Vega
                            <i class="fas fa-info-circle greek-info" data-tooltip="변동성이 1% 변할 때 옵션 가격의 변화량. 높을수록 변동성에 민감"></i>
                        </span>
                        <span class="greek-value" id="greek-vega">--</span>
                    </div>
                    <div class="greek-item">
                        <span class="greek-label">
                            IV (내재변동성)
                            <i class="fas fa-info-circle greek-info" data-tooltip="시장에서 거래되는 옵션 가격에 내포된 향후 변동성 예상치. 높을수록 시장 불확실성이 큼"></i>
                        </span>
                        <span class="greek-value" id="greek-iv">--</span>
                    </div>
                </div>
            </div>
        </section>
    `;
    
    // 기존 동적 컨텐츠 제거 후 추가
    const existingDynamic = container.querySelector('.dynamic-view-content');
    if (existingDynamic) existingDynamic.remove();
    
    container.insertAdjacentHTML('afterbegin', html);
    
    // Greeks 데이터 업데이트
    fetch('/api/market/option-chain')
        .then(response => response.json())
        .then(data => {
            updateGreeksDisplay(data.strikeChain, data.atmStrike);
        })
        .catch(error => console.error('Greeks 데이터 로드 실패:', error));
}

// 옵션체인 뷰 표시
function showChainView(container) {
    // 모든 섹션 숨기기
    container.querySelectorAll('section').forEach(section => {
        section.style.display = 'none';
    });
    
    // 옵션체인 데이터 로드 및 표시
    fetch('/api/market/option-chain')
        .then(response => response.json())
        .then(data => {
            let html = `
                <section class="option-chain-section-compact dynamic-view-content" style="display: flex; flex-direction: column; flex: 1; margin: 0;">
                    <div class="card option-chain-card-compact" style="flex: 1; display: flex; flex-direction: column;">
                        <div class="card-header-compact">
                            <div class="option-header-left">
                                <i class="fas fa-table"></i> 옵션 체인 분석
                            </div>
                            <div class="option-chain-info">
                                <span class="info-item-compact">기초: <strong id="underlying-price">${formatPrice(data.underlyingPrice)}</strong></span>
                                <span class="info-item-compact">ATM: <strong id="atm-strike">${formatPrice(data.atmStrike)}</strong></span>
                                <span class="info-item-compact max-pain-compact">
                                    <i class="fas fa-bullseye"></i> Max Pain: <strong id="max-pain">${formatPrice(data.maxPainPrice)}</strong>
                                </span>
                            </div>
                        </div>
                        <div class="card-body-compact" style="flex: 1; overflow: auto; padding: 0;">
                            <div class="option-chain-table-wrapper">
                                <table class="option-chain-table-compact">
                                    <thead>
                                        <tr>
                                            <th colspan="5" class="call-header">CALL</th>
                                            <th class="strike-header">행사가</th>
                                            <th colspan="5" class="put-header">PUT</th>
                                        </tr>
                                        <tr>
                                            <th>호가</th>
                                            <th>델타</th>
                                            <th>거래량</th>
                                            <th>미결제</th>
                                            <th>현재가</th>
                                            <th>Strike</th>
                                            <th>현재가</th>
                                            <th>미결제</th>
                                            <th>거래량</th>
                                            <th>델타</th>
                                            <th>호가</th>
                                        </tr>
                                    </thead>
                                    <tbody id="option-chain-body">
            `;
            
            if (data.strikeChain && data.strikeChain.length > 0) {
                const maxVolume = Math.max(...data.strikeChain.map(s => s.totalVolume));
                const maxOI = Math.max(...data.strikeChain.map(s => s.totalOpenInterest));
                
                data.strikeChain.forEach(strike => {
                    const isATM = strike.strikePrice == data.atmStrike;
                    const rowClass = isATM ? 'atm-row' : '';
                    
                    const callVolumeClass = strike.callVolume >= maxVolume * 0.7 ? 'high-volume' : '';
                    const putVolumeClass = strike.putVolume >= maxVolume * 0.7 ? 'high-volume' : '';
                    const callOIClass = strike.callOpenInterest >= maxOI * 0.7 ? 'high-oi' : '';
                    const putOIClass = strike.putOpenInterest >= maxOI * 0.7 ? 'high-oi' : '';
                    
                    html += `
                        <tr class="${rowClass}">
                            <td class="call-cell">${formatBidAsk(strike.callBidPrice, strike.callAskPrice)}</td>
                            <td class="call-cell">${strike.callDelta ? strike.callDelta.toFixed(3) : '-'}</td>
                            <td class="call-cell ${callVolumeClass}">${formatNumber(strike.callVolume)}</td>
                            <td class="call-cell ${callOIClass}">${formatNumber(strike.callOpenInterest)}</td>
                            <td class="call-cell formatted-number">${formatPrice(strike.callPrice)}</td>
                            <td class="strike-cell">${formatPrice(strike.strikePrice)}</td>
                            <td class="put-cell formatted-number">${formatPrice(strike.putPrice)}</td>
                            <td class="put-cell ${putOIClass}">${formatNumber(strike.putOpenInterest)}</td>
                            <td class="put-cell ${putVolumeClass}">${formatNumber(strike.putVolume)}</td>
                            <td class="put-cell">${strike.putDelta ? strike.putDelta.toFixed(3) : '-'}</td>
                            <td class="put-cell">${formatBidAsk(strike.putBidPrice, strike.putAskPrice)}</td>
                        </tr>
                    `;
                });
            } else {
                html += '<tr><td colspan="11" class="loading">데이터 없음</td></tr>';
            }
            
            html += `
                                    </tbody>
                                </table>
                            </div>
                        </div>
                    </div>
                </section>
            `;
            
            // 기존 동적 컨텐츠 제거 후 추가
            const existingDynamic = container.querySelector('.dynamic-view-content');
            if (existingDynamic) existingDynamic.remove();
            
            container.insertAdjacentHTML('afterbegin', html);
        })
        .catch(error => {
            console.error('옵션 체인 데이터 로드 실패:', error);
            container.innerHTML = '<div style="padding: 40px; text-align: center; color: white;"><h2>옵션 체인 데이터를 불러올 수 없습니다.</h2></div>';
        });
}

// 선물 테이블만 업데이트 (1초마다)
function updateFuturesTable(data) {
    const tbody = document.querySelector('.futures-view-content tbody');
    if (!tbody || !data || data.length === 0) return;
    
    let html = '';
    data.forEach((item, index) => {
        const changeClass = item.changePercent > 0 ? 'price-up' : item.changePercent < 0 ? 'price-down' : '';
        const changeSign = item.changePercent > 0 ? '+' : '';
        html += `
            <tr>
                <td><strong>${index + 1}</strong></td>
                <td>${item.symbol}</td>
                <td>${item.name}</td>
                <td class="formatted-number ${changeClass}"><strong>${formatPrice(item.currentPrice)}</strong></td>
                <td class="${changeClass}"><strong>${changeSign}${item.changePercent.toFixed(2)}%</strong></td>
                <td class="formatted-number">${formatNumber(item.volume)}</td>
                <td class="formatted-number">${formatCurrency(item.tradingValue)}</td>
                <td class="formatted-number">${formatNumber(item.openInterest)}</td>
            </tr>
        `;
    });
    tbody.innerHTML = html;
}

// 옵션 테이블만 업데이트 (1초마다)
function updateOptionsTable(data) {
    const tbody = document.querySelector('.options-view-content tbody');
    if (!tbody || !data || data.length === 0) return;
    
    let html = '';
    data.forEach((item, index) => {
        const changeClass = item.changePercent > 0 ? 'price-up' : item.changePercent < 0 ? 'price-down' : '';
        const typeClass = item.optionType === 'CALL' ? 'price-up' : 'price-down';
        const typeIcon = item.optionType === 'CALL' ? '▲' : '▼';
        const changeSign = item.changePercent > 0 ? '+' : '';
        html += `
            <tr>
                <td><strong>${index + 1}</strong></td>
                <td class="${typeClass}"><strong>${typeIcon} ${item.optionType}</strong></td>
                <td class="formatted-number"><strong>${formatPrice(item.strikePrice)}</strong></td>
                <td class="options-type">${item.symbol}</td>
                <td>${item.name}</td>
                <td class="formatted-number ${changeClass}"><strong>${formatPrice(item.currentPrice)}</strong></td>
                <td class="${changeClass}"><strong>${changeSign}${item.changePercent.toFixed(2)}%</strong></td>
                <td class="formatted-number">${formatNumber(item.volume)}</td>
                <td class="formatted-number">${formatCurrency(item.tradingValue)}</td>
                <td class="formatted-number">${formatNumber(item.openInterest)}</td>
            </tr>
        `;
    });
    tbody.innerHTML = html;
}

// 선물 데이터 + 호가창 렌더링
function renderFuturesDataWithOrderbook(container, data) {
    let html = `
        <div class="futures-with-orderbook" style="display: grid; grid-template-columns: 2fr 1fr; gap: 20px;">
            <!-- 선물 시세표 -->
            <section class="top-traded-section-compact dynamic-view-content futures-view-content" style="display: block;">
                <div class="card top-card-compact">
                    <div class="card-header-compact">
                        <i class="fas fa-rocket"></i> KOSPI200 선물 실시간 시세
                        <span style="margin-left: auto; font-size: 12px; color: rgba(255,255,255,0.8);">총 ${data ? data.length : 0}개 종목</span>
                    </div>
                    <div class="card-body-compact">
                        <table class="data-table-compact">
                            <thead>
                                <tr>
                                    <th>순위</th>
                                    <th>종목코드</th>
                                    <th>종목명</th>
                                    <th>현재가</th>
                                    <th>전일대비</th>
                                    <th>거래량</th>
                                    <th>거래대금</th>
                                    <th>미결제약정</th>
                                </tr>
                            </thead>
                            <tbody>
    `;
    
    if (data && data.length > 0) {
        data.forEach((item, index) => {
            const changeClass = item.changePercent > 0 ? 'price-up' : item.changePercent < 0 ? 'price-down' : '';
            const changeSign = item.changePercent > 0 ? '+' : '';
            html += `
                <tr>
                    <td><strong>${index + 1}</strong></td>
                    <td class="futures-type"><strong>${item.symbol}</strong></td>
                    <td>${item.name}</td>
                    <td class="formatted-number ${changeClass}"><strong>${formatPrice(item.currentPrice)}</strong></td>
                    <td class="${changeClass}"><strong>${changeSign}${item.changePercent.toFixed(2)}%</strong></td>
                    <td class="formatted-number">${formatNumber(item.volume)}</td>
                    <td class="formatted-number">${formatCurrency(item.tradingValue)}</td>
                    <td class="formatted-number">${formatNumber(item.openInterest)}</td>
                </tr>
            `;
        });
    } else {
        html += '<tr><td colspan="8" class="loading">데이터 없음</td></tr>';
    }
    
    html += `
                            </tbody>
                        </table>
                    </div>
                </div>
            </section>
            
            <!-- 호가창 (작은 크기) -->
            <section class="orderbook-compact" style="display: block;">
                <div id="orderbook-container-compact"></div>
            </section>
        </div>
    `;
    
    // 기존 동적 컨텐츠 제거 후 추가
    const existingDynamic = container.querySelector('.dynamic-view-content');
    if (existingDynamic) existingDynamic.remove();
    const existingFutures = container.querySelector('.futures-with-orderbook');
    if (existingFutures) existingFutures.remove();
    
    container.insertAdjacentHTML('afterbegin', html);
    
    // 호가창 초기화
    if (typeof OrderBook !== 'undefined') {
        OrderBook.init('101W9000', 'orderbook-container-compact');
        setTimeout(() => {
            OrderBook.loadSampleData();
        }, 300);
    }
}

// 선물 데이터 렌더링
function renderFuturesData(container, data) {
    let html = `
        <section class="top-traded-section-compact dynamic-view-content" style="display: block;">
            <div class="card top-card-compact">
                <div class="card-header-compact">
                    <i class="fas fa-rocket"></i> KOSPI200 선물 실시간 시세
                    <span style="margin-left: auto; font-size: 12px; color: rgba(255,255,255,0.8);">총 ${data ? data.length : 0}개 종목</span>
                </div>
                <div class="card-body-compact">
                    <table class="data-table-compact">
                        <thead>
                            <tr>
                                <th>순위</th>
                                <th>종목코드</th>
                                <th>종목명</th>
                                <th>현재가</th>
                                <th>전일대비</th>
                                <th>거래량</th>
                                <th>거래대금</th>
                                <th>미결제약정</th>
                            </tr>
                        </thead>
                        <tbody>
    `;
    
    if (data && data.length > 0) {
        data.forEach((item, index) => {
            const changeClass = item.changePercent > 0 ? 'price-up' : item.changePercent < 0 ? 'price-down' : '';
            const changeSign = item.changePercent > 0 ? '+' : '';
            html += `
                <tr>
                    <td><strong>${index + 1}</strong></td>
                    <td class="futures-type"><strong>${item.symbol}</strong></td>
                    <td>${item.name}</td>
                    <td class="formatted-number ${changeClass}"><strong>${formatPrice(item.currentPrice)}</strong></td>
                    <td class="${changeClass}"><strong>${changeSign}${item.changePercent.toFixed(2)}%</strong></td>
                    <td class="formatted-number">${formatNumber(item.volume)}</td>
                    <td class="formatted-number">${formatCurrency(item.tradingValue)}</td>
                    <td class="formatted-number">${formatNumber(item.openInterest)}</td>
                </tr>
            `;
        });
    } else {
        html += '<tr><td colspan="8" class="loading">데이터 없음</td></tr>';
    }
    
    html += `
                        </tbody>
                    </table>
                </div>
            </div>
        </section>
    `;
    
    // 기존 동적 컨텐츠 제거 후 추가
    const existingDynamic = container.querySelector('.dynamic-view-content');
    if (existingDynamic) existingDynamic.remove();
    
    container.insertAdjacentHTML('afterbegin', html);
}

// 옵션 데이터 렌더링
function renderOptionsData(container, data) {
    let html = `
        <section class="top-traded-section-compact dynamic-view-content" style="display: block;">
            <div class="card top-card-compact">
                <div class="card-header-compact">
                    <i class="fas fa-layer-group"></i> KOSPI200 옵션 실시간 시세
                    <span style="margin-left: auto; font-size: 12px; color: rgba(255,255,255,0.8);">총 ${data ? data.length : 0}개 종목</span>
                </div>
                <div class="card-body-compact">
                    <table class="data-table-compact">
                        <thead>
                            <tr>
                                <th>순위</th>
                                <th>타입</th>
                                <th>행사가</th>
                                <th>종목코드</th>
                                <th>종목명</th>
                                <th>현재가</th>
                                <th>전일대비</th>
                                <th>거래량</th>
                                <th>거래대금</th>
                                <th>미결제약정</th>
                            </tr>
                        </thead>
                        <tbody>
    `;
    
    if (data && data.length > 0) {
        data.forEach((item, index) => {
            const changeClass = item.changePercent > 0 ? 'price-up' : item.changePercent < 0 ? 'price-down' : '';
            const typeClass = item.optionType === 'CALL' ? 'price-up' : 'price-down';
            const typeIcon = item.optionType === 'CALL' ? '▲' : '▼';
            const changeSign = item.changePercent > 0 ? '+' : '';
            html += `
                <tr>
                    <td><strong>${index + 1}</strong></td>
                    <td class="${typeClass}"><strong>${typeIcon} ${item.optionType}</strong></td>
                    <td class="formatted-number"><strong>${formatPrice(item.strikePrice)}</strong></td>
                    <td class="options-type">${item.symbol}</td>
                    <td>${item.name}</td>
                    <td class="formatted-number ${changeClass}"><strong>${formatPrice(item.currentPrice)}</strong></td>
                    <td class="${changeClass}"><strong>${changeSign}${item.changePercent.toFixed(2)}%</strong></td>
                    <td class="formatted-number">${formatNumber(item.volume)}</td>
                    <td class="formatted-number">${formatCurrency(item.tradingValue)}</td>
                    <td class="formatted-number">${formatNumber(item.openInterest)}</td>
                </tr>
            `;
        });
    } else {
        html += '<tr><td colspan="10" class="loading">데이터 없음</td></tr>';
    }
    
    html += `
                        </tbody>
                    </table>
                </div>
            </div>
        </section>
    `;
    
    // 기존 동적 컨텐츠 제거 후 추가
    const existingDynamic = container.querySelector('.dynamic-view-content');
    if (existingDynamic) existingDynamic.remove();
    
    container.insertAdjacentHTML('afterbegin', html);
}

// 수동 데이터 새로고침
function refreshData() {
    const btn = document.querySelector('.refresh-btn-compact i');
    btn.classList.add('fa-spin');
    
    // WebSocket으로 최신 데이터 요청
    if (stompClient && stompClient.connected) {
        console.log('데이터 새로고침 요청...');
        
        // 마켓 오버뷰 다시 가져오기
        fetch('/api/market/overview')
            .then(response => response.json())
            .then(data => {
                updateMarketOverview(data);
                updateLastUpdateTime();
            })
            .catch(error => console.error('새로고침 실패:', error))
            .finally(() => {
                setTimeout(() => btn.classList.remove('fa-spin'), 500);
            });
    } else {
        alert('WebSocket 연결이 끊어졌습니다. 페이지를 새로고침해주세요.');
        btn.classList.remove('fa-spin');
    }
}

// 자동 새로고침 토글
function toggleAutoRefresh() {
    autoRefreshEnabled = document.getElementById('auto-refresh').checked;
    console.log('자동 새로고침:', autoRefreshEnabled ? 'ON' : 'OFF');
    
    if (!autoRefreshEnabled) {
        // 자동 새로고침 비활성화 시 WebSocket 일시 정지 표시
        updateConnectionStatus('일시정지', false);
    } else {
        // 재활성화 시 연결 상태 복원
        updateConnectionStatus(stompClient && stompClient.connected ? '연결됨' : '연결 중...', stompClient && stompClient.connected);
    }
}

// 연결 상태 업데이트
function updateConnectionStatus(status, isConnected) {
    const statusEl = document.getElementById('connection-status');
    if (statusEl) {
        const icon = statusEl.querySelector('i');
        const text = statusEl.querySelector('span');
        
        text.textContent = status;
        
        if (isConnected) {
            icon.className = 'fas fa-check-circle';
            statusEl.style.color = '#4caf50';
        } else {
            icon.className = 'fas fa-exclamation-circle';
            statusEl.style.color = '#ff9800';
        }
    }
}

// 마지막 업데이트 시간 표시
function updateLastUpdateTime() {
    lastUpdateTime = new Date();
    const updateEl = document.getElementById('last-update');
    if (updateEl) {
        const timeStr = lastUpdateTime.toLocaleTimeString('ko-KR', {
            hour: '2-digit',
            minute: '2-digit',
            second: '2-digit'
        });
        updateEl.querySelector('span').textContent = timeStr;
    }
}

// 투자자 안내문 모달 열기
function showInvestorNotice() {
    const modal = document.getElementById('investorNoticeModal');
    if (modal) {
        modal.style.display = 'flex';
    }
}

// ========================================
// 실시간 데이터 업데이트 함수들
// ========================================

/**
 * 실시간 선물 체결가 업데이트
 */
function updateFuturesRealtimePrice(data) {
    try {
        const symbol = data.symbol;
        const currentPrice = parseFloat(data.currentPrice);
        const volume = parseInt(data.volume);
        
        // 선물 테이블 업데이트
        const row = document.querySelector(`tr[data-symbol="${symbol}"]`);
        if (row) {
            // 현재가 업데이트
            const priceCell = row.querySelector('.current-price');
            if (priceCell) {
                priceCell.textContent = formatPrice(currentPrice);
                
                // 가격 변동 시 깜빡임 효과
                priceCell.classList.add('price-flash');
                setTimeout(() => priceCell.classList.remove('price-flash'), 500);
            }
            
            // 거래량 업데이트
            const volumeCell = row.querySelector('.volume');
            if (volumeCell) {
                volumeCell.textContent = formatNumber(volume);
            }
        }
        
        console.log(`🔄 선물 실시간: ${symbol} = ${currentPrice}`);
        
    } catch (error) {
        console.error('선물 실시간 가격 업데이트 오류:', error);
    }
}

/**
 * 실시간 선물 호가 업데이트
 */
function updateFuturesRealtimeQuote(data) {
    try {
        const symbol = data.symbol;
        const bidPrice = parseFloat(data.bidPrice1);
        const askPrice = parseFloat(data.askPrice1);
        
        // 호가 위젯 업데이트 (orderbook)
        updateOrderbookForSymbol(symbol, {
            bidPrice: bidPrice,
            askPrice: askPrice,
            bidVolume: data.bidVolume1,
            askVolume: data.askVolume1
        });
        
        console.log(`📊 선물 호가: ${symbol} = ${bidPrice}/${askPrice}`);
        
    } catch (error) {
        console.error('선물 호가 업데이트 오류:', error);
    }
}

/**
 * 실시간 옵션 체결가 업데이트
 */
function updateOptionsRealtimePrice(data) {
    try {
        const symbol = data.symbol;
        const currentPrice = parseFloat(data.currentPrice);
        const volume = parseInt(data.volume);
        
        // 옵션 테이블 업데이트
        const row = document.querySelector(`tr[data-symbol="${symbol}"]`);
        if (row) {
            // 현재가 업데이트
            const priceCell = row.querySelector('.current-price');
            if (priceCell) {
                priceCell.textContent = formatPrice(currentPrice);
                
                // 가격 변동 시 깜빡임 효과
                priceCell.classList.add('price-flash');
                setTimeout(() => priceCell.classList.remove('price-flash'), 500);
            }
            
            // 거래량 업데이트
            const volumeCell = row.querySelector('.volume');
            if (volumeCell) {
                volumeCell.textContent = formatNumber(volume);
            }
        }
        
        // 옵션 체인에서도 업데이트
        updateOptionChainPrice(symbol, currentPrice);
        
        console.log(`🔄 옵션 실시간: ${symbol} = ${currentPrice}`);
        
    } catch (error) {
        console.error('옵션 실시간 가격 업데이트 오류:', error);
    }
}

/**
 * 실시간 옵션 호가 업데이트
 */
function updateOptionsRealtimeQuote(data) {
    try {
        const symbol = data.symbol;
        const bidPrice = parseFloat(data.bidPrice1);
        const askPrice = parseFloat(data.askPrice1);
        
        // 옵션 체인에서 매도/매수 호가 업데이트
        updateOptionChainQuote(symbol, {
            bidPrice: bidPrice,
            askPrice: askPrice,
            bidVolume: data.bidVolume1,
            askVolume: data.askVolume1
        });
        
        console.log(`📊 옵션 호가: ${symbol} = ${bidPrice}/${askPrice}`);
        
    } catch (error) {
        console.error('옵션 호가 업데이트 오류:', error);
    }
}

/**
 * 호가 위젯 업데이트 (선물)
 */
function updateOrderbookForSymbol(symbol, quoteData) {
    const orderbookWidget = document.querySelector('.orderbook-widget');
    if (!orderbookWidget) return;
    
    const currentSymbol = orderbookWidget.dataset.symbol;
    if (currentSymbol !== symbol) return;  // 다른 종목이면 무시
    
    // 매도호가 업데이트
    const askPriceEl = orderbookWidget.querySelector('.ask-price');
    if (askPriceEl) {
        askPriceEl.textContent = formatPrice(quoteData.askPrice);
    }
    
    // 매수호가 업데이트
    const bidPriceEl = orderbookWidget.querySelector('.bid-price');
    if (bidPriceEl) {
        bidPriceEl.textContent = formatPrice(quoteData.bidPrice);
    }
}

/**
 * 옵션 체인에서 가격 업데이트
 */
function updateOptionChainPrice(symbol, price) {
    const priceCell = document.querySelector(`.option-chain-row [data-symbol="${symbol}"] .option-price`);
    if (priceCell) {
        priceCell.textContent = formatPrice(price);
        
        // 깜빡임 효과
        priceCell.classList.add('price-flash');
        setTimeout(() => priceCell.classList.remove('price-flash'), 500);
    }
}

/**
 * 옵션 체인에서 호가 업데이트
 */
function updateOptionChainQuote(symbol, quoteData) {
    // 매도호가
    const askCell = document.querySelector(`.option-chain-row [data-symbol="${symbol}"] .ask-price`);
    if (askCell) {
        askCell.textContent = formatPrice(quoteData.askPrice);
    }
    
    // 매수호가
    const bidCell = document.querySelector(`.option-chain-row [data-symbol="${symbol}"] .bid-price`);
    if (bidCell) {
        bidCell.textContent = formatPrice(quoteData.bidPrice);
    }
}

// 투자자 안내문 모달 닫기
function closeInvestorNotice() {
    const modal = document.getElementById('investorNoticeModal');
    if (modal) {
        modal.style.display = 'none';
    }
}

// 모달 외부 클릭 시 닫기
window.addEventListener('click', function(event) {
    const modal = document.getElementById('investorNoticeModal');
    if (event.target === modal) {
        closeInvestorNotice();
    }
});

// Disconnect on page unload
window.addEventListener('beforeunload', function() {
    if (stompClient !== null) {
        stompClient.disconnect();
    }
});
