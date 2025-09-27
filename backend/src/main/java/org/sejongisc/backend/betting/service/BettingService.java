package org.sejongisc.backend.betting.service;

import lombok.RequiredArgsConstructor;
import org.sejongisc.backend.betting.entity.BetRound;
import org.sejongisc.backend.betting.entity.Scope;
import org.sejongisc.backend.betting.entity.Stock;
import org.sejongisc.backend.betting.repository.BetRoundRepository;
import org.sejongisc.backend.betting.repository.StockRepository;
import org.sejongisc.backend.common.exception.CustomException;
import org.sejongisc.backend.common.exception.ErrorCode;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Random;

@Service
@RequiredArgsConstructor
public class BettingService {

    private final BetRoundRepository betRoundRepository;
    private final StockRepository stockRepository;

    private final Random random = new Random();

    public Stock getStock(){
        List<Stock> stocks = stockRepository.findAll();
        if (stocks.isEmpty()) {
            throw new CustomException(ErrorCode.STOCK_NOT_FOUND);
        }
        // TODO : 가중치 랜덤설정
        return stocks.get(random.nextInt(stocks.size()));
    }

    public boolean setAllowFree(){
        return random.nextDouble() < 0.2;
    }

    public void createBetRound(Scope scope) {
        Stock stock = getStock();
        LocalDateTime now = LocalDateTime.now();

        BetRound betRound = BetRound.builder()
                .scope(scope)
                .title(now.toLocalDate() + " " + stock.getName() + " " + scope.name() + " 라운드")
                .symbol(stock.getSymbol())
                .allowFree(setAllowFree())
                .status(true)
                .openAt(scope.getOpenAt(now))
                .lockAt(scope.getLockAt(now))
                .market(stock.getMarket())
                .previousClosePrice(stock.getPreviousClosePrice())
                .build();

        betRoundRepository.save(betRound);
    }

}
