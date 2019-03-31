package fr.lelouet.collectionholders.examples;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import fr.lelouet.collectionholders.examples.direct.Holder;
import fr.lelouet.collectionholders.examples.direct.Store;
import fr.lelouet.collectionholders.examples.direct.StoreHolder;

@SuppressWarnings("unused")
public class Example1 {

	/** fetch the lowest price from a list of stores */
	public static void main(String... args) {
		List<Store> stores = new ArrayList<>();
		Item item = null;

		// direct sequential method
		{
			int min = Integer.MAX_VALUE;
			for( Store store : stores) {
				min=Math.min(min, store.getPrice(item));
			}
		}
		// with parallel stream to be faster
		{
			int min = stores.parallelStream().mapToInt(s -> s.getPrice(item)).min().orElseGet(() -> Integer.MAX_VALUE);
		}
		// with holders
		List<StoreHolder> storeHolders = new ArrayList<>();
		{
			List<Holder<Integer>> holders = storeHolders.parallelStream().map(s -> s.getPriceHolder(item))
					.collect(Collectors.toList());
			int min = holders.stream().mapToInt(h -> h.get()).min().orElseGet(() -> Integer.MAX_VALUE);
		}
	}

}
