package eu.darken.bluemusic.util.mvp;

public interface TypedObjectFactory<TypeT> {
    TypeT create();

    Class<? extends TypeT> getTypeClazz();
}
