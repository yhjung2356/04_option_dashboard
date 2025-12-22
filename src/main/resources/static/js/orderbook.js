// ========================================
// 호가창 (Market Depth / Order Book)
// ========================================

const OrderBook = {
    currentSymbol: null,
    maxDepth: 10,
    
    // 호가창 초기화
    init(symbol, containerId = 'orderbook-container') {
        this.currentSymbol = symbol;
        const container = document.getElementById(containerId);
        
        if (!container) {
            console.error('호가창 컨테이너를 찾을 수 없습니다:', containerId);
            return;
        }
        
        // HTML 구조 생성
        container.innerHTML = this.generateHTML();
        
        // 실시간 데이터 구독 (WebSocket)
        this.subscribe(symbol);
        
        console.log('✅ 호가창 초기화 완료:', symbol);
    },
    
    // 호가창 HTML 생성
    generateHTML() {
        return `
            <div class="orderbook-container">
                <div class="orderbook-header">
                    <div class="orderbook-title">
                        <i class="fas fa-layer-group"></i>
                        실시간 호가창
                    </div>
                    <div class="orderbook-symbol">${this.currentSymbol || ''}</div>
                </div>
                
                <!-- 호가 테이블 -->
                <table class="orderbook-table">
                    <thead>
                        <tr>
                            <th>매도잔량</th>
                            <th>호가</th>
                            <th>매수잔량</th>
                        </tr>
                    </thead>
                    <tbody id="orderbook-tbody">
                        ${this.generateLoadingRows()}
                    </tbody>
                </table>
                
                <!-- 스프레드 정보 -->
                <div class="orderbook-spread">
                    <span class="orderbook-spread-label">스프레드:</span>
                    <span class="orderbook-spread-value" id="orderbook-spread">-</span>
                </div>
                
                <!-- 호가 잔량 요약 -->
                <div class="orderbook-summary">
                    <div class="orderbook-summary-item">
                        <div class="orderbook-summary-label">매도 총잔량</div>
                        <div class="orderbook-summary-value ask" id="total-ask-volume">-</div>
                    </div>
                    <div class="orderbook-summary-item">
                        <div class="orderbook-summary-label">매수/매도 비율</div>
                        <div class="orderbook-summary-value" id="bid-ask-ratio">-</div>
                    </div>
                    <div class="orderbook-summary-item">
                        <div class="orderbook-summary-label">매수 총잔량</div>
                        <div class="orderbook-summary-value bid" id="total-bid-volume">-</div>
                    </div>
                </div>
            </div>
        `;
    },
    
    // 로딩 중 표시
    generateLoadingRows() {
        let html = '';
        for (let i = 0; i < this.maxDepth; i++) {
            html += `
                <tr>
                    <td class="orderbook-loading" colspan="3">
                        <i class="fas fa-spinner fa-spin"></i>
                    </td>
                </tr>
            `;
        }
        return html;
    },
    
    // 호가 데이터 업데이트
    update(data) {
        if (!data || !data.asks || !data.bids) {
            console.warn('호가 데이터가 유효하지 않습니다:', data);
            return;
        }
        
        const tbody = document.getElementById('orderbook-tbody');
        if (!tbody) return;
        
        // 최대 거래량 계산 (바 그래프용)
        const maxVolume = this.calculateMaxVolume(data);
        
        // HTML 생성
        let html = '';
        
        // 매도 호가 (위에서 아래로)
        const asks = data.asks.slice(0, this.maxDepth).reverse();
        asks.forEach((ask, index) => {
            const percent = (ask.volume / maxVolume) * 100;
            html += this.generateAskRow(ask, percent);
        });
        
        // 현재가 행
        if (data.currentPrice) {
            html += this.generateCurrentPriceRow(data.currentPrice, data.priceChange);
        }
        
        // 매수 호가 (위에서 아래로)
        const bids = data.bids.slice(0, this.maxDepth);
        bids.forEach((bid, index) => {
            const percent = (bid.volume / maxVolume) * 100;
            html += this.generateBidRow(bid, percent);
        });
        
        tbody.innerHTML = html;
        
        // 요약 정보 업데이트
        this.updateSummary(data);
        
        console.log('📊 호가창 업데이트 완료');
    },
    
    // 매도 호가 행 생성
    generateAskRow(ask, percent) {
        return `
            <tr>
                <td class="orderbook-ask-volume orderbook-volume-cell">
                    <div class="orderbook-ask-bar" style="width: ${percent}%"></div>
                    <span class="orderbook-volume-text">${this.formatVolume(ask.volume)}</span>
                </td>
                <td class="orderbook-ask-price">${this.formatPrice(ask.price)}</td>
                <td></td>
            </tr>
        `;
    },
    
    // 매수 호가 행 생성
    generateBidRow(bid, percent) {
        return `
            <tr>
                <td></td>
                <td class="orderbook-bid-price">${this.formatPrice(bid.price)}</td>
                <td class="orderbook-bid-volume orderbook-volume-cell">
                    <div class="orderbook-bid-bar" style="width: ${percent}%"></div>
                    <span class="orderbook-volume-text">${this.formatVolume(bid.volume)}</span>
                </td>
            </tr>
        `;
    },
    
    // 현재가 행 생성
    generateCurrentPriceRow(price, change) {
        const changeClass = change >= 0 ? 'price-up' : 'price-down';
        const changeIcon = change >= 0 ? '▲' : '▼';
        return `
            <tr class="orderbook-current-row">
                <td colspan="3" class="orderbook-current-price">
                    <i class="fas fa-circle"></i>
                    <span class="${changeClass}">${this.formatPrice(price)}</span>
                    <span class="${changeClass}">${changeIcon} ${Math.abs(change).toFixed(2)}</span>
                </td>
            </tr>
        `;
    },
    
    // 요약 정보 업데이트
    updateSummary(data) {
        const totalAsk = data.asks.reduce((sum, ask) => sum + ask.volume, 0);
        const totalBid = data.bids.reduce((sum, bid) => sum + bid.volume, 0);
        const ratio = totalBid / totalAsk;
        
        // 매도 총잔량
        const totalAskEl = document.getElementById('total-ask-volume');
        if (totalAskEl) totalAskEl.textContent = this.formatVolume(totalAsk);
        
        // 매수 총잔량
        const totalBidEl = document.getElementById('total-bid-volume');
        if (totalBidEl) totalBidEl.textContent = this.formatVolume(totalBid);
        
        // 비율
        const ratioEl = document.getElementById('bid-ask-ratio');
        if (ratioEl) {
            ratioEl.textContent = ratio.toFixed(2);
            ratioEl.style.color = ratio > 1 ? '#2196F3' : '#f44336';
        }
        
        // 스프레드
        if (data.asks.length > 0 && data.bids.length > 0) {
            const spread = data.asks[0].price - data.bids[0].price;
            const spreadEl = document.getElementById('orderbook-spread');
            if (spreadEl) spreadEl.textContent = this.formatPrice(spread);
        }
    },
    
    // 최대 거래량 계산
    calculateMaxVolume(data) {
        let maxVol = 0;
        data.asks.forEach(ask => {
            if (ask.volume > maxVol) maxVol = ask.volume;
        });
        data.bids.forEach(bid => {
            if (bid.volume > maxVol) maxVol = bid.volume;
        });
        return maxVol;
    },
    
    // 가격 포맷팅
    formatPrice(price) {
        return price.toFixed(2);
    },
    
    // 거래량 포맷팅
    formatVolume(volume) {
        if (volume >= 10000) {
            return (volume / 10000).toFixed(1) + '만';
        } else if (volume >= 1000) {
            return (volume / 1000).toFixed(1) + '천';
        }
        return volume.toString();
    },
    
    // WebSocket 구독
    subscribe(symbol) {
        console.log('🔌 호가창 WebSocket 구독:', symbol);
        
        // 한국투자증권 API WebSocket 연결
        // 실제로는 서버에서 중계하는 방식 사용
        if (stompClient && stompClient.connected) {
            stompClient.subscribe(`/topic/orderbook/${symbol}`, (message) => {
                const data = JSON.parse(message.body);
                this.update(data);
            });
        }
    },
    
    // 샘플 데이터 로드 (테스트용)
    loadSampleData() {
        const sampleData = {
            symbol: this.currentSymbol,
            currentPrice: 325.50,
            priceChange: 2.30,
            asks: [
                { price: 326.00, volume: 1250 },
                { price: 325.95, volume: 980 },
                { price: 325.90, volume: 1540 },
                { price: 325.85, volume: 720 },
                { price: 325.80, volume: 2100 },
                { price: 325.75, volume: 890 },
                { price: 325.70, volume: 1650 },
                { price: 325.65, volume: 430 },
                { price: 325.60, volume: 1980 },
                { price: 325.55, volume: 1120 }
            ],
            bids: [
                { price: 325.45, volume: 1870 },
                { price: 325.40, volume: 950 },
                { price: 325.35, volume: 2340 },
                { price: 325.30, volume: 680 },
                { price: 325.25, volume: 1560 },
                { price: 325.20, volume: 920 },
                { price: 325.15, volume: 1790 },
                { price: 325.10, volume: 540 },
                { price: 325.05, volume: 2210 },
                { price: 325.00, volume: 1340 }
            ]
        };
        
        this.update(sampleData);
    }
};

// 전역으로 export
window.OrderBook = OrderBook;
