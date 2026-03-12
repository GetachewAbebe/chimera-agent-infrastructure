package org.chimera.contracts;

import java.util.List;
import org.chimera.model.TrendSignal;

public interface TrendFetcher {
  List<TrendSignal> fetchTopTrends(String niche, int limit);
}
