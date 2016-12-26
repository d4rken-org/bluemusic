package eu.darken.bluetoothmanager.util.mvp;

public interface TypedObjectFactory<TypeT> {
    TypeT create();

    Class<? extends TypeT> getTypeClazz();
}
