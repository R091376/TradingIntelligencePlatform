package com.tip.api;

import com.tip.api.dto.AddWatchlistRequest;
import com.tip.watchlist.WatchlistEntry;
import com.tip.watchlist.WatchlistService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/watchlist")
public class WatchlistController {

    private final WatchlistService watchlistService;

    public WatchlistController(WatchlistService watchlistService) {
        this.watchlistService = watchlistService;
    }

    /**
     * List public-active watchlist entries (insertion order) with bootstrapStatus.
     */
    @GetMapping
    public List<WatchlistEntry> list() {
        return watchlistService.listActive();
    }

    /**
     * Blocking add by trading symbol. Returns 200 with final entry (READY or FAILED).
     * 404 unknown symbol; 409 duplicate or at hard max 50.
     */
    @PostMapping
    public WatchlistEntry add(@RequestBody AddWatchlistRequest request) {
        String symbol = request != null ? request.symbol() : null;
        return watchlistService.add(symbol);
    }

    /**
     * Remove symbol. Path captures instrument keys with {@code |} and spaces
     * ({@code {symbolId:.+}}). Returns 204; 404 if not on list.
     */
    @DeleteMapping("/{symbolId:.+}")
    public ResponseEntity<Void> remove(@PathVariable("symbolId") String symbolId) {
        watchlistService.remove(symbolId);
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }
}
