package lt.lb.uncheckedutils;

import java.util.Objects;

/**
 *
 * @author laim0nas100
 * @param <T>
 */
public abstract class SafeOptBase<T> implements SafeOpt<T> {

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 23 * hash + Objects.hashCode(this.rawValue());
        hash = 23 * hash + Objects.hashCode(this.rawException());
        return hash;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (obj instanceof SafeOpt) {
            final SafeOpt<?> other = (SafeOpt<?>) obj;
            if (!Objects.equals(this.rawValue(), other.rawValue())) {
                return false;
            }
            return Objects.equals(this.rawException(), other.rawException());
        } else {
            return false;
        }

    }

    @Override
    public String toString() {
        T val = rawValue();
        Throwable threw = rawException();
        if (val != null) {
            return String.format("SafeOpt[%s]", val);
        }
        if (threw != null) {
            return String.format("SafeOpt.error[%s]", threw);
        }
        return "SafeOpt.empty";
    }
}
