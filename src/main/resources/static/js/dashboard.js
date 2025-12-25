/* ===================================================================
   Futures/Options Dashboard - Main JavaScript
   Author: Dashboard Team
   Last Modified: 2025-12-25
   
   Modules:
   - State Management
   - WebSocket Connection
   - Page Snapshot
   - Market Data Updates
   - Time & Status Management
   =================================================================== */

'use strict';

// ===================================================================
// Global State Management
// ===================================================================
const StateManager = (() => {
    const state = window.dashboardState || {
        dataSource: 'KIS',
        demoMode: false,
        marketHoursEnabled: true,
        initialTimestamp: 0,
        isConnected: false,
        lastUpdate: null,
        currentView: 'overview'
    };

    return {
        // 서버에서 현재 상태 가져오기
        async fetchSystemState() {
            try {
                const response = await fetch('/api/market/state');
                if (!response.ok) throw new Error(`HTTP ${response.status}`);
                
                const newState = await response.json();
                this.updateState(newState);
                return newState;
            } catch (error) {
                console.error('[StateManager] 상태 조회 실패:', error);
                return null;
            }
        },

        // 상태 업데이트
        updateState(newState) {
            Object.assign(state, newState);
            console.log('[StateManager] 상태 업데이트됨:', state);
            
            // 커스텀 이벤트 발생
            window.dispatchEvent(new CustomEvent('stateChanged', { 
                detail: Object.freeze({...state})
            }));
        },

        // 현재 상태 가져오기
        getState() {
            return Object.freeze({...state});
        },

        // 특정 상태 값 가져오기
        get(key) {
            return state[key];
        },

        // 특정 상태 값 설정
        set(key, value) {
            state[key] = value;
            this.saveToLocalStorage();
        },

        // 로컬스토리지에 저장
        saveToLocalStorage() {
            try {
                localStorage.setItem('dashboardState', JSON.stringify(state));
            } catch (error) {
                console.error('[StateManager] 로컬스토리지 저장 실패:', error);
            }
        },

        // 로컬스토리지에서 복원
        restoreFromLocalStorage() {
            try {
                const saved = localStorage.getItem('dashboardState');
                if (saved) {
                    const savedState = JSON.parse(saved);
                    Object.assign(state, savedState);
                    console.log('[StateManager] 저장된 상태 복원됨:', state);
                }
            } catch (error) {
                console.error('[StateManager] 로컬스토리지 복원 실패:', error);
            }
        }
    };
})();

// ===================================================================
// Page Snapshot & Export
// ===================================================================
const PageSnapshot = (() => {
    // 현재 페이지 상태를 JSON으로 추출
    function captureState() {
        return {
            capturedAt: new Date().toISOString(),
            capturedTime: document.getElementById('current-time')?.textContent || '',
            marketStatus: document.getElementById('status-text')?.textContent || '',
            
            systemState: {
                dataSource: StateManager.get('dataSource'),
                isConnected: StateManager.get('isConnected'),
                demoMode: StateManager.get('demoMode')
            },
            
            futures: {
                volume: document.getElementById('futures-volume')?.textContent || '0',
                tradingValue: document.getElementById('futures-value')?.textContent || '0',
                openInterest: document.getElementById('futures-oi')?.textContent || '0'
            },
            
            options: {
                volume: document.getElementById('options-volume')?.textContent || '0',
                tradingValue: document.getElementById('options-value')?.textContent || '0',
                openInterest: document.getElementById('options-oi')?.textContent || '0'
            },
            
            putCallRatio: {
                volumeRatio: document.getElementById('pc-ratio-volume')?.textContent || '0.00',
                openInterestRatio: document.getElementById('pc-ratio-oi')?.textContent || '0.00',
                tradingValueRatio: document.getElementById('pc-ratio-value')?.textContent || '0.00'
            },
            
            topByVolume: captureTableData('top-by-volume'),
            topByOpenInterest: captureTableData('top-by-oi'),
            
            optionChain: {
                underlyingPrice: document.getElementById('underlying-price')?.textContent || '0',
                atmStrike: document.getElementById('atm-strike')?.textContent || '0',
                maxPain: document.getElementById('max-pain')?.textContent || '0',
                data: captureTableData('option-chain-body', true)
            }
        };
    }

    // 테이블 데이터 추출
    function captureTableData(tableId, isOptionChain = false) {
        const tbody = document.getElementById(tableId);
        if (!tbody) return [];
        
        const rows = tbody.querySelectorAll('tr');
        const data = [];
        
        rows.forEach(row => {
            const cells = row.querySelectorAll('td');
            if (cells.length > 0 && !row.textContent.includes('로딩')) {
                const rowData = Array.from(cells).map(cell => cell.textContent.trim());
                data.push(rowData);
            }
        });
        
        return data;
    }

    // 텍스트 형식으로 포맷팅
    function formatAsText(snapshot) {
        let text = '╔════════════════════════════════════════════════════════════════╗\n';
        text += '║         선물/옵션 실시간 거래 대시보드 스냅샷                 ║\n';
        text += '╚════════════════════════════════════════════════════════════════╝\n\n';
        
        text += `📅 캡처 시간: ${snapshot.capturedTime}\n`;
        text += `📊 시장 상태: ${snapshot.marketStatus}\n`;
        text += `💾 데이터 소스: ${snapshot.systemState.dataSource}\n`;
        text += `🔌 연결 상태: ${snapshot.systemState.isConnected ? '✅ 연결됨' : '❌ 연결 안됨'}\n\n`;
        
        text += '━'.repeat(64) + '\n';
        text += '🚀 선물 전체\n';
        text += '━'.repeat(64) + '\n';
        text += `   거래량:     ${snapshot.futures.volume}\n`;
        text += `   거래대금:   ${snapshot.futures.tradingValue}\n`;
        text += `   미결제약정: ${snapshot.futures.openInterest}\n\n`;
        
        text += '━'.repeat(64) + '\n';
        text += '📊 옵션 전체\n';
        text += '━'.repeat(64) + '\n';
        text += `   거래량:     ${snapshot.options.volume}\n`;
        text += `   거래대금:   ${snapshot.options.tradingValue}\n`;
        text += `   미결제약정: ${snapshot.options.openInterest}\n\n`;
        
        text += '━'.repeat(64) + '\n';
        text += '⚖️  Put/Call Ratio\n';
        text += '━'.repeat(64) + '\n';
        text += `   거래량 Ratio:   ${snapshot.putCallRatio.volumeRatio}\n`;
        text += `   미결제 Ratio:   ${snapshot.putCallRatio.openInterestRatio}\n`;
        text += `   거래대금 Ratio: ${snapshot.putCallRatio.tradingValueRatio}\n\n`;
        
        if (snapshot.topByVolume && snapshot.topByVolume.length > 0) {
            text += '━'.repeat(64) + '\n';
            text += '📈 거래량 TOP 5\n';
            text += '━'.repeat(64) + '\n';
            snapshot.topByVolume.slice(0, 5).forEach((row, idx) => {
                text += `${String(idx + 1).padStart(2, ' ')}. ${row.join(' | ')}\n`;
            });
        }
        
        if (snapshot.topByOpenInterest && snapshot.topByOpenInterest.length > 0) {
            text += '\n' + '━'.repeat(64) + '\n';
            text += '🔥 미결제약정 TOP 5\n';
            text += '━'.repeat(64) + '\n';
            snapshot.topByOpenInterest.slice(0, 5).forEach((row, idx) => {
                text += `${String(idx + 1).padStart(2, ' ')}. ${row.join(' | ')}\n`;
            });
        }
        
        text += '\n' + '━'.repeat(64) + '\n';
        text += '📋 옵션 체인 정보\n';
        text += '━'.repeat(64) + '\n';
        text += `   기초자산:   ${snapshot.optionChain.underlyingPrice}\n`;
        text += `   ATM 행사가: ${snapshot.optionChain.atmStrike}\n`;
        text += `   Max Pain:   ${snapshot.optionChain.maxPain}\n`;
        
        text += '\n' + '═'.repeat(64) + '\n';
        text += `생성 일시: ${new Date().toLocaleString('ko-KR')}\n`;
        text += '═'.repeat(64) + '\n';
        
        return text;
    }

    // 알림 표시
    function showNotification(message, type = 'info') {
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
        
        setTimeout(() => {
            notification.style.animation = 'slideOutRight 0.3s ease';
            setTimeout(() => notification.remove(), 300);
        }, 3000);
    }

    // 타임스탬프 생성
    function getTimestamp() {
        return new Date().toISOString().replace(/[:.]/g, '-').slice(0, 19);
    }

    return {
        // JSON 다운로드
        downloadJSON() {
            const snapshot = captureState();
            const dataStr = JSON.stringify(snapshot, null, 2);
            const dataBlob = new Blob([dataStr], { type: 'application/json' });
            
            const url = URL.createObjectURL(dataBlob);
            const link = document.createElement('a');
            link.href = url;
            link.download = `dashboard-snapshot-${getTimestamp()}.json`;
            link.click();
            
            URL.revokeObjectURL(url);
            showNotification('📄 JSON 파일이 다운로드되었습니다!', 'success');
        },

        // 텍스트 형식으로 다운로드
        downloadText() {
            const snapshot = captureState();
            const text = formatAsText(snapshot);
            const dataBlob = new Blob([text], { type: 'text/plain; charset=utf-8' });
            
            const url = URL.createObjectURL(dataBlob);
            const link = document.createElement('a');
            link.href = url;
            link.download = `dashboard-report-${getTimestamp()}.txt`;
            link.click();
            
            URL.revokeObjectURL(url);
            showNotification('💾 텍스트 리포트가 다운로드되었습니다!', 'success');
        },

        // 클립보드에 복사
        async copyToClipboard() {
            const snapshot = captureState();
            const text = formatAsText(snapshot);
            
            try {
                await navigator.clipboard.writeText(text);
                showNotification('✅ 페이지 상태가 클립보드에 복사되었습니다!\n\n어디든 붙여넣기(Ctrl+V) 하실 수 있습니다.', 'success');
            } catch (err) {
                // Fallback
                copyToClipboardFallback(text);
            }
        },

        // 콘솔에 출력
        printToConsole() {
            const snapshot = captureState();
            console.group('대시보드 현재 상태');
            console.log(snapshot);
            console.log('\n텍스트 형식:');
            console.log(formatAsText(snapshot));
            console.groupEnd();
        }
    };

    // Fallback: textarea 사용
    function copyToClipboardFallback(text) {
        const textarea = document.createElement('textarea');
        textarea.value = text;
        textarea.style.cssText = 'position:fixed;opacity:0';
        document.body.appendChild(textarea);
        textarea.select();
        
        try {
            document.execCommand('copy');
            showNotification('✅ 클립보드 복사 완료!', 'success');
        } catch (err) {
            showNotification('❌ 클립보드 복사 실패', 'error');
        }
        
        document.body.removeChild(textarea);
    }
})();

// 전역 접근
window.PageSnapshot = PageSnapshot;

// ===================================================================
// WebSocket Connection
// ===================================================================
const WebSocketManager = (() => {
    let stompClient = null;
    let reconnectAttempts = 0;
    const MAX_RECONNECT_ATTEMPTS = 5;
    const RECONNECT_DELAY = 3000;

    function connect() {
        try {
            const socket = new SockJS('/ws');
            stompClient = Stomp.over(socket);
            
            // Stomp 로그 비활성화 (프로덕션)
            stompClient.debug = null;
            
            stompClient.connect({}, onConnected, onError);
        } catch (error) {
            console.error('[WebSocket] 연결 생성 실패:', error);
            scheduleReconnect();
        }
    }

    function onConnected(frame) {
        console.log('[WebSocket] 연결 성공:', frame);
        StateManager.set('isConnected', true);
        StateManager.set('lastUpdate', new Date().toISOString());
        reconnectAttempts = 0;
        
        // 시장 데이터 구독
        stompClient.subscribe('/topic/market-overview', (message) => {
            try {
                const data = JSON.parse(message.body);
                MarketDataUpdater.updateMarketOverview(data);
            } catch (error) {
                console.error('[WebSocket] 메시지 파싱 실패:', error);
            }
        });
    }

    function onError(error) {
        console.error('[WebSocket] 연결 에러:', error);
        StateManager.set('isConnected', false);
        scheduleReconnect();
    }

    function scheduleReconnect() {
        if (reconnectAttempts < MAX_RECONNECT_ATTEMPTS) {
            reconnectAttempts++;
            console.log(`[WebSocket] ${RECONNECT_DELAY/1000}초 후 재연결 시도 (${reconnectAttempts}/${MAX_RECONNECT_ATTEMPTS})`);
            setTimeout(connect, RECONNECT_DELAY);
        } else {
            console.error('[WebSocket] 최대 재연결 시도 횟수 초과');
        }
    }

    function disconnect() {
        if (stompClient !== null) {
            stompClient.disconnect();
            StateManager.set('isConnected', false);
            console.log('[WebSocket] 연결 종료');
        }
    }

    return {
        connect,
        disconnect
    };
})();

// ===================================================================
// Market Data Updater
// ===================================================================
const MarketDataUpdater = (() => {
    function updateMarketOverview(data) {
        // 선물 데이터 업데이트
        updateElement('futures-volume', formatNumber(data.totalFuturesVolume));
        updateElement('futures-value', formatCurrency(data.totalFuturesTradingValue));
        updateElement('futures-oi', formatNumber(data.totalFuturesOpenInterest));
        
        // 옵션 데이터 업데이트
        updateElement('options-volume', formatNumber(data.totalOptionsVolume));
        updateElement('options-value', formatCurrency(data.totalOptionsTradingValue));
        updateElement('options-oi', formatNumber(data.totalOptionsOpenInterest));
        
        // Put/Call Ratio 업데이트
        if (data.putCallRatio) {
            updatePutCallRatio(data.putCallRatio);
            updateMarketSentiment(data.putCallRatio);
        }
        
        // 상위 종목 업데이트
        if (data.topByVolume) updateTopTradedTable('top-by-volume', data.topByVolume);
        if (data.topByOpenInterest) updateTopTradedTable('top-by-oi', data.topByOpenInterest);
    }

    function updatePutCallRatio(ratio) {
        updateElement('pc-ratio-volume', ratio.volumeRatio.toFixed(2));
        updateElement('pc-ratio-oi', ratio.openInterestRatio.toFixed(2));
        updateElement('pc-ratio-value', ratio.tradingValueRatio.toFixed(2));
        
        updateRatioColor('pc-ratio-volume', ratio.volumeRatio);
        updateRatioColor('pc-ratio-oi', ratio.openInterestRatio);
        updateRatioColor('pc-ratio-value', ratio.tradingValueRatio);
    }

    function updateRatioColor(elementId, ratio) {
        const element = document.getElementById(elementId);
        if (!element) return;
        
        element.classList.remove('price-up', 'price-down');
        if (ratio > 1) {
            element.classList.add('price-down'); // Bearish
        } else if (ratio < 0.7) {
            element.classList.add('price-up'); // Bullish
        }
    }

    function updateMarketSentiment(putCallRatio) {
        const avgRatio = (putCallRatio.volumeRatio + putCallRatio.openInterestRatio) / 2;
        
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
            fillElement.setAttribute('aria-valuenow', sentimentValue);
        }
        if (labelElement) labelElement.textContent = sentimentLabel;
    }

    function updateTopTradedTable(tableId, data) {
        const tbody = document.getElementById(tableId);
        if (!tbody) return;
        
        if (!data || data.length === 0) {
            tbody.innerHTML = '<tr><td colspan="7" class="loading">데이터 없음</td></tr>';
            return;
        }
        
        let html = '';
        data.forEach((item, index) => {
            const typeClass = item.type === 'FUTURES' ? 'futures-type' : 'options-type';
            const changeClass = item.changePercent > 0 ? 'price-up' : 
                               item.changePercent < 0 ? 'price-down' : '';
            
            html += `
                <tr>
                    <td>${index + 1}</td>
                    <td class="${typeClass}">${escapeHtml(item.symbol)}</td>
                    <td>${escapeHtml(item.name)}</td>
                    <td class="formatted-number">${formatPrice(item.currentPrice)}</td>
                    <td class="formatted-number">${formatNumber(item.volume)}</td>
                    <td class="formatted-number">${formatCurrency(item.tradingValue)}</td>
                    <td class="formatted-number">${formatNumber(item.openInterest)}</td>
                </tr>
            `;
        });
        
        tbody.innerHTML = html;
    }

    function updateElement(id, value) {
        const element = document.getElementById(id);
        if (element) element.textContent = value;
    }

    return {
        updateMarketOverview
    };
})();

// ===================================================================
// Option Chain Manager
// ===================================================================
const OptionChainManager = (() => {
    async function fetchAndUpdate() {
        try {
            const response = await fetch('/api/market/option-chain');
            if (!response.ok) throw new Error(`HTTP ${response.status}`);
            
            const data = await response.json();
            updateOptionChainData(data);
        } catch (error) {
            console.error('[OptionChain] 데이터 로딩 실패:', error);
        }
    }

    function updateOptionChainData(data) {
        // 옵션 체인 정보 업데이트
        updateElement('underlying-price', formatPrice(data.underlyingPrice));
        updateElement('atm-strike', formatPrice(data.atmStrike));
        updateElement('max-pain', formatPrice(data.maxPainPrice));
        
        // Greeks 업데이트
        updateGreeksDisplay(data.strikeChain, data.atmStrike);
        
        // 옵션 체인 테이블 업데이트
        updateOptionChainTable(data);
    }

    function updateGreeksDisplay(strikeChain, atmStrike) {
        if (!strikeChain || strikeChain.length === 0) return;
        
        const atmData = strikeChain.find(s => s.strikePrice == atmStrike);
        if (!atmData) return;
        
        // Delta 업데이트
        updateElement('delta-call', atmData.callDelta ? atmData.callDelta.toFixed(3) : '--');
        updateElement('delta-put', atmData.putDelta ? atmData.putDelta.toFixed(3) : '--');
        
        // 기타 Greeks 업데이트
        updateElement('greek-gamma', atmData.callGamma ? atmData.callGamma.toFixed(4) : '--');
        updateElement('greek-theta', atmData.callTheta ? atmData.callTheta.toFixed(4) : '--');
        updateElement('greek-vega', atmData.callVega ? atmData.callVega.toFixed(4) : '--');
        
        // IV 업데이트
        if (atmData.callImpliedVolatility) {
            const iv = (atmData.callImpliedVolatility * 100).toFixed(2) + '%';
            updateElement('greek-iv', iv);
            updateElement('iv-index', (atmData.callImpliedVolatility * 100).toFixed(1));
        }
    }

    function updateOptionChainTable(data) {
        const tbody = document.getElementById('option-chain-body');
        if (!tbody) return;
        
        if (!data.strikeChain || data.strikeChain.length === 0) {
            tbody.innerHTML = '<tr><td colspan="11" class="loading">데이터 없음</td></tr>';
            return;
        }
        
        let html = '';
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
        
        tbody.innerHTML = html;
    }

    function updateElement(id, value) {
        const element = document.getElementById(id);
        if (element) element.textContent = value;
    }

    return {
        fetchAndUpdate
    };
})();

// ===================================================================
// Time & Market Status Manager
// ===================================================================
const TimeStatusManager = (() => {
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
        
        const timeElement = document.getElementById('current-time');
        if (timeElement) {
            timeElement.textContent = timeString;
            timeElement.setAttribute('datetime', now.toISOString());
        }
        
        updateMarketStatus(now);
    }

    function updateMarketStatus(now) {
        const day = now.getDay();
        const hours = now.getHours();
        const minutes = now.getMinutes();
        const time = hours * 100 + minutes;
        
        const statusElement = document.getElementById('market-status');
        const statusText = document.getElementById('status-text');
        const statusIcon = statusElement?.querySelector('i');
        const closedBanner = document.getElementById('market-closed-banner');
        
        if (!statusElement || !statusText || !statusIcon) return;
        
        // 주말 체크
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

    return {
        updateTime,
        start() {
            updateTime();
            setInterval(updateTime, 1000);
        }
    };
})();

// ===================================================================
// Utility Functions
// ===================================================================
function formatNumber(num) {
    if (!num) return '0';
    return num.toLocaleString('ko-KR');
}

function formatCurrency(num) {
    if (!num) return '0원';
    
    if (num >= 100000000) {
        return (num / 100000000).toFixed(1) + '억원';
    } else if (num >= 10000) {
        return (num / 10000).toFixed(0) + '만원';
    }
    
    return num.toLocaleString('ko-KR') + '원';
}

function formatPrice(price) {
    if (!price) return '-';
    return parseFloat(price).toFixed(2);
}

function formatBidAsk(bid, ask) {
    if (!bid || !ask) return '-';
    return `${formatPrice(bid)}/${formatPrice(ask)}`;
}

function escapeHtml(text) {
    const map = {
        '&': '&amp;',
        '<': '&lt;',
        '>': '&gt;',
        '"': '&quot;',
        "'": '&#039;'
    };
    return String(text).replace(/[&<>"']/g, m => map[m]);
}

// ===================================================================
// REST API Data Loader
// ===================================================================
async function loadInitialData() {
    console.log('[Init] 초기 데이터 로딩 시작');
    
    try {
        // 시장 개요 로드
        const overviewResponse = await fetch('/api/market/overview');
        if (overviewResponse.ok) {
            const overviewData = await overviewResponse.json();
            MarketDataUpdater.updateMarketOverview(overviewData);
        }
        
        // 옵션 체인 로드
        const optionChainResponse = await fetch('/api/market/option-chain');
        if (optionChainResponse.ok) {
            const optionChainData = await optionChainResponse.json();
            OptionChainManager.updateOptionChainData(optionChainData);
        }
        
        console.log('[Init] 초기 데이터 로딩 완료');
    } catch (error) {
        console.error('[Init] 초기 데이터 로딩 실패:', error);
    }
}

// ===================================================================
// Modal Functions
// ===================================================================
function showInvestorNotice() {
    const modal = document.getElementById('investorNoticeModal');
    if (modal) {
        modal.style.display = 'flex';
        modal.setAttribute('aria-hidden', 'false');
    }
}

function closeInvestorNotice() {
    const modal = document.getElementById('investorNoticeModal');
    if (modal) {
        modal.style.display = 'none';
        modal.setAttribute('aria-hidden', 'true');
    }
}

// ===================================================================
// Event Listeners
// ===================================================================

// 모달 외부 클릭 시 닫기
window.addEventListener('click', (event) => {
    const modal = document.getElementById('investorNoticeModal');
    if (event.target === modal) {
        closeInvestorNotice();
    }
});

// 단축키 등록
document.addEventListener('keydown', (e) => {
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
        }
    }
});

// 페이지 언로드 시 연결 종료
window.addEventListener('beforeunload', () => {
    WebSocketManager.disconnect();
});

// ===================================================================
// Application Initialization
// ===================================================================
document.addEventListener('DOMContentLoaded', async () => {
    console.log('[Dashboard] 초기화 시작');
    
    // 상태 복원 (선택사항)
    StateManager.restoreFromLocalStorage();
    
    // 서버 상태 가져오기 (선택사항)
    await StateManager.fetchSystemState();
    
    // 초기 데이터 로드
    await loadInitialData();
    
    // WebSocket 연결
    WebSocketManager.connect();
    
    // 시간 & 상태 업데이트 시작
    TimeStatusManager.start();
    
    // 옵션 체인 주기적 업데이트 (2초마다)
    setInterval(() => OptionChainManager.fetchAndUpdate(), 2000);
    
    console.log('[Dashboard] 초기화 완료');
    console.log('[Dashboard] 현재 상태:', StateManager.getState());
});

// 전역 함수 노출 (HTML에서 사용)
window.showInvestorNotice = showInvestorNotice;
window.closeInvestorNotice = closeInvestorNotice;
