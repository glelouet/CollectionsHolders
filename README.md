# CollectionsHolders
synchronized updated collections

## What role

Main goal : to implement both observable and stream of modification together in synchronized collections.
Typically useful when you have several items you want to fetch, an they may be already fetched (or not), or they may be modified.

## USe case and reason


This collection is made for, when you fetch resources over internet with a cache system.

### Parallel and sequential calls

For example you want to get the lower price of one item, given several stores.

#### Sequential code

Typically you would do something like 

~~~~java
int min = Integer.MAX_VALUE;
for( Store store : stores)
  min=Math.min(min, store.getPrice(item));
~~~~

The issue with this sequential code is that the time to get the price is linear with the number of stores you have. If you have a huge number of stores, you will have a huge delay between the moment you start this code and the moment it exits.

#### Parallel code

So you usually want to get them in parallel. Using streams seems a good idea : 

~~~~java
int min = stores.parallelStream().maptToInt(s->s.getPrice(item)).min().orElseGet(()->Integer.MAX_VALUE)
~~~~

This way you are limited by only one request time (constant time). But is it really ? Actually, the parallelStream() works over a fixed amount of threads, eg 10. We decreased the time but it's still linear. Whatever is our number of threads, this algorithm still scales linearly with the number of stores times the amount of time needed to perform one request.

#### Data holder

What would be better, is to start the fetch in a new thread for each store ; then once all stores have returned, we iterate over the prices. Assuming spawning a new thread is done by the store, that would do : 

~~~~java
List<Holder<Integer>> holders = storeHolders.parallelStream().map(s->s.getPriceHolder(item)).collect(Collectors.toList());
int min = holders.stream().mapToInt(h->h.get()).min().orElseGet(()->Integer.MAX_VALUE);
~~~~

Of course we need to have a correct Store implementation, that creates the holder when needed, start a thread, and put the data in the holder when the thread terminates. However this can lead to some performances issues with a high number of requests, especially if we use threads (because threads are actually limited).

### Caching the data

In case we do a lot of requests at the same time, it may be wise to use caching. Caching allows us to keep a resource in memory once we request it, so next calls to that resource will return the cached value instead of fetching it again.

The resource is kept as long as it's relevant, typically the store returns not only the price, but also the expiration date.

#### Lazy caching

Lazy caching consists in fetching the resource again when we need that data and the expiration is reached. Since the data may be requested by several concurrent threads, we need to synchronize to be sure we don't fetch the same resource several times.
Removing the synchronization, it becomes

~~~~java
int getPrice(Item item) {
	if (!cachedPrices().containsKey(item)) {
	  Requested<Integer> ret = fetchPrice(item);
	  cachedPrices().put(item, ret);
	  return ret.get();
	}
	Requested<Integer> ret = cachedPrices().get(item);
	if (ret.getExpiry().getTime() <= System.currentTimeMillis()) {
	  ret = fetchPrice(item);
	  cachedPrices().put(item, ret);
	  return ret.get();
	}
	return ret.get();
}
~~~~

But now we have another issue : each call is done in sequential order, so we can't use parallelism, unless we actually return a holder on the price. Which means, we need to cache a holder on the price, also containing the expiration date.

#### Active caching

Active caching consists in fetching the resource once when needed, and then fetching it again when the expiration date is reached. Typically this is done by a dedicated updater manager.
The interest lies on the idea that after the first request, all call will be almost instantaneous. 

The issue is that the first call can still block the process for a long time. For this reason, caching and returning not the data but a holder is the way to go.
In this case, it may be convenient to also be notified whenever the data stored in the holder is modified.

The collections in this project aim at providing the holders for such a use case.