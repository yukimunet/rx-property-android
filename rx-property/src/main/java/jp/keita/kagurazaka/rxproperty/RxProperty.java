package jp.keita.kagurazaka.rxproperty;

import android.databinding.ObservableField;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import java.util.EnumSet;

import rx.Observable;
import rx.Subscriber;
import rx.Subscription;
import rx.subjects.BehaviorSubject;
import rx.subjects.PublishSubject;
import rx.subjects.SerializedSubject;

/**
 * Two-way bindable and observable property for Android Data Binding.
 */
public class RxProperty<T> implements ReadOnlyRxProperty<T> {

  /**
   * Mode of the {@link RxProperty}.
   */
  public enum Mode {
    /**
     * All mode is off.
     */
    NONE,
    /**
     * If next value is same as current, not set and not notify.
     */
    DISTINCT_UNTIL_CHANGED,
    /**
     * Sends notification on the instance created and subscribed.
     */
    RAISE_LATEST_VALUE_ON_SUBSCRIBE;
  }

  private static final EnumSet<Mode> DEFAULT_MODE
      = EnumSet.of(Mode.DISTINCT_UNTIL_CHANGED, Mode.RAISE_LATEST_VALUE_ON_SUBSCRIBE);

  private final boolean isDistinctUntilChanged;

  private final RxPropertyField<T> field;
  private final SerializedSubject<T, T> subject;
  private Subscription sourceSubscription;

  /**
   * Creates {@code RxProperty} without an initial value.
   */
  public RxProperty() {
    this((T) null);
  }

  /**
   * Creates {@code RxProperty} with the initial value.
   *
   * @param initialValue the initial value of this {@code RxProperty}
   */
  public RxProperty(@Nullable T initialValue) {
    this(initialValue, DEFAULT_MODE);
  }

  /**
   * Creates {@code RxProperty} with the specified mode.
   *
   * @param mode mode of this {@code RxProperty}
   */
  public RxProperty(@NonNull EnumSet<Mode> mode) {
    this((T) null, mode);
  }

  /**
   * Creates {@code RxProperty} with the initial value and the specified mode.
   *
   * @param initialValue the initial value of this {@code RxProperty}
   * @param mode         mode of this {@code RxProperty}
   */
  public RxProperty(@Nullable T initialValue, @NonNull EnumSet<Mode> mode) {
    this(null, initialValue, mode);
  }

  /**
   * Creates {@code RxProperty} from the specified {@link Observable}.
   *
   * @param source a source {@link Observable} of this {@code RxProperty}
   */
  public RxProperty(@NonNull Observable<T> source) {
    this(source, DEFAULT_MODE);
  }

  /**
   * Creates {@code RxProperty} from the specified {@link Observable} with the initial value.
   *
   * @param source       a source {@link Observable} of this {@code RxProperty}
   * @param initialValue the initial value of this {@code RxProperty}
   */
  public RxProperty(@NonNull Observable<T> source, @Nullable T initialValue) {
    this(source, initialValue, DEFAULT_MODE);
  }

  /**
   * Creates {@code RxProperty} from the specified {@link Observable} with the specified mode.
   *
   * @param source a source {@link Observable} of this {@code RxProperty}
   * @param mode   mode of this {@code RxProperty}
   */
  public RxProperty(@NonNull Observable<T> source, @NonNull EnumSet<Mode> mode) {
    this(source, null, mode);
  }

  /**
   * Creates {@code RxProperty} from the specified {@link Observable} with the initial value and the
   * specified mode.
   *
   * @param source       a source {@link Observable} of this {@code RxProperty}
   * @param initialValue the initial value of this {@code RxProperty}
   * @param mode         mode of this {@code RxProperty}
   */
  public RxProperty(@Nullable Observable<T> source, @Nullable T initialValue,
                    @NonNull EnumSet<Mode> mode) {
    field = new RxPropertyField<>(this, initialValue);

    // Set modes.
    isDistinctUntilChanged
        = !mode.contains(Mode.NONE) && mode.contains(Mode.DISTINCT_UNTIL_CHANGED);
    boolean isRaiseLatestValueOnSubscribe
        = !mode.contains(Mode.NONE) && mode.contains(Mode.RAISE_LATEST_VALUE_ON_SUBSCRIBE);

    // Create emitter.
    subject = new SerializedSubject<>(
        isRaiseLatestValueOnSubscribe ?
        BehaviorSubject.create(initialValue) :
        PublishSubject.<T>create()
    );

    // Subscribe the source Observable.
    if (source == null) {
      sourceSubscription = null;
    } else {
      sourceSubscription = source.subscribe(new Subscriber<T>() {
        @Override
        public void onNext(T value) {
          setValue(value);
        }

        @Override
        public void onError(Throwable e) {
          subject.onError(e);
        }

        @Override
        public void onCompleted() {
          subject.onCompleted();
        }
      });
    }
  }

  /**
   * Gets a hot {@link Observable} to emit values of this {@code RxProperty}.
   *
   * @return a hot {@link Observable} to emit values of this {@code RxProperty}
   */
  @Override
  public Observable<T> asObservable() {
    return subject.asObservable();
  }

  /**
   * Gets the latest value of this {@code RxProperty}.
   *
   * @return the latest value stored in this {@code RxProperty}
   */
  @Override
  public T getValue() {
    return field.get();
  }

  /**
   * Sets the specified value to this {@code RxProperty}. The change will be notified to both bound
   * views and observers of this {@code RxProperty}.
   *
   * @param value a value to set
   */
  public void setValue(T value) {
    field.set(value, true);
  }

  /**
   * Sets the specified value to this {@code RxProperty}. The change will be notified to observers
   * of this {@code RxProperty} but not affect bound views.
   *
   * @param value a value to set
   */
  public void setValueAsView(T value) {
    field.set(value, false);
  }

  /**
   * Sets the specified value to this {@code RxProperty}. The change will be notified to observers
   * of this {@code RxProperty} but not affect bound views.
   *
   * @param value a value to set
   * @param notify will be notified to views
   * @param emit will be notified to observers
   */
  public void setValue(T value, boolean notify, boolean emit) {
    field.set(value, notify, emit);
  }

  /**
   * Stops receiving notifications by the source {@link Observable} and send notifications to
   * observers of this {@code RxProperty}.
   */
  @Override
  public void unsubscribe() {
    if (!isUnsubscribed()) {
      sourceSubscription.unsubscribe();
    }
    sourceSubscription = null;
  }

  /**
   * Indicates whether this {@code RxProperty} is currently unsubscribed.
   *
   * @return {@code true} if this {@code RxProperty} has no {@link Observable} as source or is
   * currently unsubscribed, {@code false} otherwise
   */
  @Override
  public boolean isUnsubscribed() {
    return sourceSubscription == null || sourceSubscription.isUnsubscribed();
  }

  /**
   * Emits the specified value to observers of this {@code RxProperty}.
   *
   * @param value a value to emit
   */
  void emitValue(T value) {
    if (isDistinctUntilChanged && compare(value, getValue())) {
      return;
    }
    subject.onNext(value);
  }

  private static <T> boolean compare(T value1, T value2) {
    return (value1 == null && value2 == null) || (value1 != null && value1.equals(value2));
  }

  /**
   * @deprecated This is a magic method for Data Binding. Don't call it in your code.
   */
  @Deprecated
  @Override
  public ObservableField<T> getGet() {
    return field;
  }

  /**
   * @deprecated This is a magic method for Data Binding. Don't call it in your code.
   */
  @Deprecated
  @Override
  public void setGet(ObservableField<T> value) {
    throw new UnsupportedOperationException();
  }

  /**
   * @deprecated This is a magic method for Data Binding. Don't call it in your code.
   */
  @Deprecated
  public RxProperty<T> getSet() {
    return this;
  }

  /**
   * @deprecated This is a magic method for Data Binding. Don't call it in your code.
   */
  @Deprecated
  public void setSet(RxProperty<T> value) {
    throw new UnsupportedOperationException();
  }

  /**
   * Reimplementation of {@link ObservableField} for collaborating with {@link RxProperty}.
   *
   * @param <T> a type of value stored in this property.
   */
  private static class RxPropertyField<T> extends ObservableField<T> {
    private final RxProperty<T> parent;
    private T value;

    RxPropertyField(RxProperty<T> parent, T initialValue) {
      this.parent = parent;
      this.value = initialValue;
    }

    @Override
    public T get() {
      return value;
    }

    @Override
    public void set(T value) {
      set(value, false);
    }

    void set(T value, boolean notify) {
      set(value, notify, true);
    }

    void set(T value, boolean notify, boolean emit) {
      if (emit) {
        parent.emitValue(value);
      }
      if (!compare(value, this.value)) {
        this.value = value;
        if (notify) {
          notifyChange();
        }
      }
    }
  }
}
