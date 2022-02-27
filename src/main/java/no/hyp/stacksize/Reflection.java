package no.hyp.stacksize;


import xyz.jpenilla.reflectionremapper.ReflectionRemapper;
import xyz.jpenilla.reflectionremapper.proxy.ReflectionProxyFactory;

public final class Reflection {
  public static final ItemProxy ITEM_PROXY;
  public static final MaterialProxy MATERIAL_PROXY;

  static {
    // ReflectionRemapper loads mappings into memory, which can be quite large, so once we are done with it don't store a ref anywhere so it can be gc'd
    final ReflectionRemapper reflectionRemapper = ReflectionRemapper.forReobfMappingsInPaperJar();
    // ReflectionProxyFactory stores a ref to it's ReflectionRemapper
    final ReflectionProxyFactory reflectionProxyFactory = ReflectionProxyFactory.create(reflectionRemapper, Reflection.class.getClassLoader());

    // proxy instances are safe to hold onto
    ITEM_PROXY = reflectionProxyFactory.reflectionProxy(ItemProxy.class);
    MATERIAL_PROXY = reflectionProxyFactory.reflectionProxy(MaterialProxy.class);
  }
}

