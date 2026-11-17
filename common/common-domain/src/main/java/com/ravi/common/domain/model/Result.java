package com.ravi.common.domain.model;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Functional Result type demonstrating Java 21 Sealed Interfaces and Pattern Matching.
 * Eliminates unexpected runtime nulls and checked exception bubbling.
 *
 * @param <T> Successful value type
 * @param <E> Error value type
 */
public sealed interface Result<T, E> permits Result.Success, Result.Failure {

    boolean isSuccess();

    boolean isFailure();

    Optional<T> getSuccess();

    Optional<E> getFailure();

    <U> Result<U, E> map(Function<? super T, ? extends U> mapper);

    <F> Result<T, F> mapError(Function<? super E, ? extends F> mapper);

    Result<T, E> onSuccess(Consumer<? super T> action);

    Result<T, E> onFailure(Consumer<? super E> action);

    static <T, E> Result<T, E> success(T value) {
        return new Success<>(Objects.requireNonNull(value, "Success value cannot be null"));
    }

    static <T, E> Result<T, E> failure(E error) {
        return new Failure<>(Objects.requireNonNull(error, "Error value cannot be null"));
    }

    record Success<T, E>(T value) implements Result<T, E> {
        @Override
        public boolean isSuccess() {
            return true;
        }

        @Override
        public boolean isFailure() {
            return false;
        }

        @Override
        public Optional<T> getSuccess() {
            return Optional.of(value);
        }

        @Override
        public Optional<E> getFailure() {
            return Optional.empty();
        }

        @Override
        public <U> Result<U, E> map(Function<? super T, ? extends U> mapper) {
            return new Success<>(mapper.apply(value));
        }

        @Override
        public <F> Result<T, F> mapError(Function<? super E, ? extends F> mapper) {
            return new Success<>(value);
        }

        @Override
        public Result<T, E> onSuccess(Consumer<? super T> action) {
            action.accept(value);
            return this;
        }

        @Override
        public Result<T, E> onFailure(Consumer<? super E> action) {
            return this;
        }
    }

    record Failure<T, E>(E error) implements Result<T, E> {
        @Override
        public boolean isSuccess() {
            return false;
        }

        @Override
        public boolean isFailure() {
            return true;
        }

        @Override
        public Optional<T> getSuccess() {
            return Optional.empty();
        }

        @Override
        public Optional<E> getFailure() {
            return Optional.of(error);
        }

        @Override
        public <U> Result<U, E> map(Function<? super T, ? extends U> mapper) {
            return new Failure<>(error);
        }

        @Override
        public <F> Result<T, F> mapError(Function<? super E, ? extends F> mapper) {
            return new Failure<>(mapper.apply(error));
        }

        @Override
        public Result<T, E> onSuccess(Consumer<? super T> action) {
            return this;
        }

        @Override
        public Result<T, E> onFailure(Consumer<? super E> action) {
            action.accept(error);
            return this;
        }
    }
}
