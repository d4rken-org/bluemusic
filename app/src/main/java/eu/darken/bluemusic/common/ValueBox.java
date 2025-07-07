package eu.darken.bluemusic.common;

public class ValueBox<T> {
    private T value;

    public ValueBox() { }

    public ValueBox(T value) {
        this.value = value;
    }

    public T getValue() {
        return value;
    }

    public void setValue(T value) {
        this.value = value;
    }
}
