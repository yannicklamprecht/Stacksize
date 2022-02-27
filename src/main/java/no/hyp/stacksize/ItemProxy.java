package no.hyp.stacksize;

import net.minecraft.world.item.Item;
import xyz.jpenilla.reflectionremapper.proxy.annotation.FieldSetter;
import xyz.jpenilla.reflectionremapper.proxy.annotation.Proxies;

@Proxies(Item.class)
public interface ItemProxy {
  @FieldSetter("maxStackSize")
  void setMaxStackSize(Item instance, int stackSize);
}
