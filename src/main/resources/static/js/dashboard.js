// WebSocket connection
let stompClient = null;

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
    const socket = new SockJS('/ws');
    stompClient = Stomp.over(socket);
    
    stompClient.connect({}, function(frame) {
        console.log('Connected: ' + frame);
        StateManager.set('isConnected', true);
        StateManager.set('lastUpdate', new Date().toISOString());
        
        // Subscribe to market overview updates
        stompClient.subscribe('/topic/market-overview', function(message) {
            const data = JSON.parse(message.body);
            updateMarketOverview(data);
        });
    });
}

// Update market overview
function updateMarketOverview(data) {
    // Update futures data
    document.getElementById('futures-volume').textContent = formatNumber(data.totalFuturesVolume);
    document.getElementById('futures-value').textContent = formatCurrency(data.totalFuturesTradingValue);
    document.getElementById('futures-oi').textContent = formatNumber(data.totalFuturesOpenInterest);
    
    // Update options data
    document.getElementById('options-volume').textContent = formatNumber(data.totalOptionsVolume);
    document.getElementById('options-value').textContent = formatCurrency(data.totalOptionsTradingValue);
    document.getElementById('options-oi').textContent = formatNumber(data.totalOptionsOpenInterest);
    
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

// Format bid/ask
function formatBidAsk(bid, ask) {
    if (!bid || !ask) return '-';
    return `${formatPrice(bid)}/${formatPrice(ask)}`;
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
function updateMarketStatus(now) {
    const day = now.getDay(); // 0=일요일, 6=토요일
    const hours = now.getHours();
    const minutes = now.getMinutes();
    const time = hours * 100 + minutes;
    
    const statusElement = document.getElementById('market-status');
    const statusText = document.getElementById('status-text');
    const statusIcon = statusElement.querySelector('i');
    const closedBanner = document.getElementById('market-closed-banner');
    
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

// Update Greeks display
function updateGreeksDisplay(strikeChain, atmStrike) {
    if (!strikeChain || strikeChain.length === 0) {
        console.log('No strike chain data available for Greeks');
        return;
    }
    
    // Find ATM strike
    const atmData = strikeChain.find(s => s.strikePrice == atmStrike);
    if (!atmData) {
        console.log('ATM strike not found:', atmStrike);
        return;
    }
    
    console.log('ATM Data for Greeks:', atmData);
    
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
            const ivPercent = (iv * 100).toFixed(2) + '%';
            ivElement.textContent = ivPercent;
            console.log('IV Updated:', ivPercent);
            
            // Update IV index in sentiment card
            const ivIndexElement = document.getElementById('iv-index');
            if (ivIndexElement) {
                const ivValue = (iv * 100).toFixed(1);
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

// 투자자 안내문 모달 열기
function showInvestorNotice() {
    const modal = document.getElementById('investorNoticeModal');
    if (modal) {
        modal.style.display = 'flex';
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
