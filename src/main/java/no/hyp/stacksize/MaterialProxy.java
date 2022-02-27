package no.hyp.stacksize;

import org.bukkit.Material;

import xyz.jpenilla.reflectionremapper.proxy.annotation.FieldSetter;
import xyz.jpenilla.reflectionremapper.proxy.annotation.Proxies;

@Proxies(Material.class)
public interface MaterialProxy {
  @FieldSetter("maxStack")
  void setMaxStackSize(Material instance, int stackSize);
}
