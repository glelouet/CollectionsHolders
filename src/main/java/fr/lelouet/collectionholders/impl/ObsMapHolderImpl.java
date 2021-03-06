package fr.lelouet.collectionholders.impl;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.function.Consumer;
import java.util.function.Function;

import fr.lelouet.collectionholders.interfaces.ObsListHolder;
import fr.lelouet.collectionholders.interfaces.ObsMapHolder;
import fr.lelouet.syncbarker.LockWatchDog;
import javafx.beans.Observable;
import javafx.collections.FXCollections;
import javafx.collections.MapChangeListener;
import javafx.collections.ObservableMap;

public class ObsMapHolderImpl<K, U> implements ObsMapHolder<K, U> {

	private ObservableMap<K, U> underlying;

	public ObsMapHolderImpl(ObservableMap<K, U> underlying) {
		this(underlying, false);
	}

	/**
	 * crate a new {@link ObsMapHolderImpl} backing on an underlying
	 * {@link ObservableMap}
	 *
	 * @param underlying
	 *          the map to back to
	 * @param datareceived
	 *          whether the map already contains all the information possible. if
	 *          not, call to synchronized method will wait until the data is
	 *          received
	 */
	public ObsMapHolderImpl(ObservableMap<K, U> underlying, boolean datareceived) {
		this.underlying = underlying;
		if (datareceived) {
			dataReceived();
		}
	}

	private CountDownLatch dataReceivedLatch = new CountDownLatch(1);

	private ArrayList<Consumer<Map<K, U>>> receiveListeners;

	@Override
	public void waitData() {
		try {
			dataReceivedLatch.await();
		} catch (InterruptedException e) {
			throw new UnsupportedOperationException("catch this", e);
		}
	}

	@Override
	public Map<K, U> copy() {
		waitData();
		Map<K, U> ret;
		LockWatchDog.BARKER.tak(underlying);
		synchronized (underlying) {
			LockWatchDog.BARKER.hld(underlying);
			ret = new HashMap<>(underlying);
		}
		LockWatchDog.BARKER.rel(underlying);
		return ret;
	}

	@Override
	public U get(K key) {
		waitData();
		return underlying.get(key);
	}

	@Override
	public void follow(MapChangeListener<? super K, ? super U> listener) {
		LockWatchDog.BARKER.tak(underlying);
		synchronized (underlying) {
			LockWatchDog.BARKER.hld(underlying);
			ObservableMap<K, U> othermap = FXCollections.observableHashMap();
			othermap.addListener(listener);
			othermap.putAll(underlying);
			underlying.addListener(listener);
		}
		LockWatchDog.BARKER.rel(underlying);
	}

	@Override
	public Observable asObservable() {
		return underlying;
	}

	@Override
	public void addReceivedListener(Consumer<Map<K, U>> callback) {
		synchronized (underlying) {
			if (receiveListeners == null) {
				receiveListeners = new ArrayList<>();
			}
			receiveListeners.add(callback);
			if (dataReceivedLatch.getCount() == 0) {
				callback.accept(underlying);
			}
		}
	}

	@Override
	public boolean remReceivedListener(Consumer<Map<K, U>> callback) {
		synchronized (underlying) {
			return receiveListeners.remove(callback);
		}
	}

	@Override
	public void dataReceived() {
		dataReceivedLatch.countDown();
		if (receiveListeners != null) {
			Map<K, U> consumed = underlying;
			for (Consumer<Map<K, U>> r : receiveListeners) {
				r.accept(consumed);
			}
		}
	}

	@Override
	public void unfollow(MapChangeListener<? super K, ? super U> change) {
		LockWatchDog.BARKER.tak(underlying);
		synchronized (underlying) {
			LockWatchDog.BARKER.hld(underlying);
			underlying.removeListener(change);
		}
		LockWatchDog.BARKER.rel(underlying);
	}

	/**
	 * create a new observableMap that map each entry in the source to an entry in
	 * the ret. creation and deletion of key are mappecd accordingly.
	 *
	 * @param source
	 * @param mapping
	 * @return
	 */
	public static <K, S, T> ObsMapHolderImpl<K, T> map(ObsMapHolder<K, S> source, Function<S, T> mapping) {
		ObservableMap<K, T> containedTarget = FXCollections.observableHashMap();
		ObsMapHolderImpl<K, T> ret = new ObsMapHolderImpl<>(containedTarget);
		source.follow(c -> {
			if (c.wasRemoved() && !c.wasAdded()) {
				synchronized (containedTarget) {
					containedTarget.remove(c.getKey());
				}
			} else {
				synchronized (containedTarget) {
					containedTarget.put(c.getKey(), mapping.apply(c.getValueAdded()));
				}
			}
		});
		source.addReceivedListener(l -> ret.dataReceived());
		return ret;
	}

	/**
	 * transforms an observable list into a map, by extracting the key from the
	 * new elements.
	 *
	 * @param list
	 * @param keyExtractor
	 * @return
	 */
	public static <K, V> ObsMapHolderImpl<K, V> toMap(ObsListHolder<V> list, Function<V, K> keyExtractor) {
		return toMap(list, keyExtractor, o -> o);
	}

	/**
	 * transforms an observable list into a map, by extracting the key from the
	 * new elements and remapping them to a new type.
	 *
	 * @param list
	 * @param keyExtractor
	 *          function to create the new keys of the map
	 * @param remapper
	 *          function to create the new values of the map
	 * @return
	 */
	public static <K, V, L> ObsMapHolderImpl<K, L> toMap(ObsListHolder<V> list, Function<V, K> keyExtractor,
			Function<V, L> remapper) {
		ObservableMap<K, L> internal = FXCollections.observableHashMap();
		ObsMapHolderImpl<K, L> ret = new ObsMapHolderImpl<>(internal);
		list.follow(c -> {
			while (c.next()) {
				synchronized (internal) {
					if (c.wasRemoved()) {
						for (V removed : c.getRemoved()) {
							internal.remove(keyExtractor.apply(removed));
						}
					}
					if (c.wasAdded()) {
						for (V added : c.getAddedSubList()) {
							internal.put(keyExtractor.apply(added), remapper.apply(added));
						}
					}
				}
			}
		});
		list.addReceivedListener(l -> {
			ret.dataReceived();
		});
		return ret;
	}

}
