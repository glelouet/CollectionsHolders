package fr.lelouet.collectionholders.examples.lazycache;

import java.util.HashMap;

import fr.lelouet.collectionholders.examples.Item;

public interface StoreCache {

	public HashMap<Item, Requested<Integer>> cachedPrices();

	public Requested<Integer> fetchPrice(Item item);

	default int getPrice(Item item) {
		if (!cachedPrices().containsKey(item)) {
			synchronized (cachedPrices()) {
				if (!cachedPrices().containsKey(item)) {
					Requested<Integer> ret = fetchPrice(item);
					cachedPrices().put(item, ret);
					return ret.get();
				}
			}
		}
		Requested<Integer> ret = cachedPrices().get(item);
		if (ret.getExpiry().getTime() <= System.currentTimeMillis()) {
			synchronized (cachedPrices()) {
				if (ret == cachedPrices().get(item)) {
					ret = fetchPrice(item);
					cachedPrices().put(item, ret);
					return ret.get();
				}
			}
		}
		return ret.get();
	}

}
