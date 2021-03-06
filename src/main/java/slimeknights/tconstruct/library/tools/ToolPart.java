package slimeknights.tconstruct.library.tools;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

import net.minecraft.client.gui.FontRenderer;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.util.text.translation.I18n;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.util.List;
import java.util.ListIterator;
import java.util.Map;

import javax.annotation.Nonnull;

import slimeknights.mantle.util.LocUtils;
import slimeknights.tconstruct.common.ClientProxy;
import slimeknights.tconstruct.common.config.Config;
import slimeknights.tconstruct.library.TinkerRegistry;
import slimeknights.tconstruct.library.Util;
import slimeknights.tconstruct.library.materials.IMaterialStats;
import slimeknights.tconstruct.library.materials.Material;
import slimeknights.tconstruct.library.tinkering.MaterialItem;
import slimeknights.tconstruct.library.tinkering.PartMaterialType;
import slimeknights.tconstruct.library.traits.ITrait;
import slimeknights.tconstruct.library.utils.TagUtil;
import slimeknights.tconstruct.library.utils.Tags;

public class ToolPart extends MaterialItem implements IToolPart {

  protected int cost;

  public ToolPart(int cost) {
    this.setCreativeTab(TinkerRegistry.tabParts);
    this.cost = cost;
  }

  @Override
  public int getCost() {
    return cost;
  }

  @Override
  public void getSubItems(@Nonnull Item itemIn, CreativeTabs tab, List<ItemStack> subItems) {
    for(Material mat : TinkerRegistry.getAllMaterials()) {
      // check if the material makes sense for this item (is it usable to build stuff?)
      if(canUseMaterial(mat)) {
        subItems.add(getItemstackWithMaterial(mat));
        if(!Config.listAllMaterials) {
          break;
        }
      }
    }
  }


  @Override
  public boolean canUseMaterial(Material mat) {
    for(ToolCore tool : TinkerRegistry.getTools()) {
      for(PartMaterialType pmt : tool.getRequiredComponents()) {
        if(pmt.isValid(this, mat)) {
          return true;
        }
      }
    }

    return false;
  }

  @Override
  public ItemStack getItemstackWithMaterial(Material material) {
    return super.getItemstackWithMaterial(material);
  }

  @SideOnly(Side.CLIENT)
  @Override
  public void addInformation(ItemStack stack, EntityPlayer playerIn, List<String> tooltip, boolean advanced) {
    Material material = getMaterial(stack);

    // Material traits/info
    boolean shift = Util.isShiftKeyDown();

    if(!checkMissingMaterialTooltip(stack, tooltip)) {
      tooltip.addAll(getTooltipTraitInfo(material));
    }

    // Stats
    if(Config.extraTooltips) {
      if(!shift) {
        // info tooltip for detailed and component info
        tooltip.add("");
        tooltip.add(Util.translate("tooltip.tool.holdShift"));
      }
      else {
        for(IMaterialStats stat : material.getAllStats()) {
          if(hasUseForStat(stat.getIdentifier())) {
            List<String> text = stat.getLocalizedInfo();
            if(!text.isEmpty()) {
              tooltip.add("");
              tooltip.add(TextFormatting.WHITE.toString() + TextFormatting.UNDERLINE + stat.getLocalizedName());
              tooltip.addAll(stat.getLocalizedInfo());
            }
          }
        }
      }
    }

    String materialInfo = I18n.translateToLocalFormatted("tooltip.part.material_added_by",
                                                         TinkerRegistry.getTrace(material));
    tooltip.add("");
    tooltip.add(materialInfo);
  }

  public List<String> getTooltipTraitInfo(Material material) {
    // We build a map with Stat -> Traits mappings that allows us to group or not group depending on what's available
    Map<String, List<ITrait>> mapping = Maps.newConcurrentMap();

    // go through all stats of the material, and check if they have a use, build the map from them
    for(IMaterialStats stat : material.getAllStats()) {
      if(hasUseForStat(stat.getIdentifier())) {
        List<ITrait> traits = material.getAllTraitsForStats(stat.getIdentifier());
        if(!traits.isEmpty()) {
          boolean unified = false;
          for(Map.Entry<String, List<ITrait>> entry : mapping.entrySet()) {
            // group together if identical
            if(entry.getValue().equals(traits)) {
              mapping.put(entry.getKey() + ", " + stat.getLocalizedName(), entry.getValue());
              mapping.remove(entry.getKey());
              unified = true;
              break;
            }
          }

          if(!unified) {
            mapping.put(stat.getLocalizedName(), traits);
          }
        }
      }
    }

    List<String> tooltips = Lists.newLinkedList();
    boolean withType = mapping.size() > 1;

    // convert the entries into tooltips
    for(Map.Entry<String, List<ITrait>> entry : mapping.entrySet()) {
      // add the traits in "Stattype: Trait1, Trait2,..." style
      StringBuilder sb = new StringBuilder();
      if(withType) {
        sb.append(TextFormatting.ITALIC.toString());
        sb.append(entry.getKey());
        sb.append(": ");
        sb.append(TextFormatting.RESET.toString());
      }
      sb.append(material.getTextColor());
      List<ITrait> traits = entry.getValue();
      if(!traits.isEmpty()) {
        ListIterator<ITrait> iter = traits.listIterator();

        sb.append(iter.next().getLocalizedName());
        while(iter.hasNext()) {
          sb.append(", ").append(iter.next().getLocalizedName());
        }

        tooltips.add(sb.toString());
      }
    }

    return tooltips;
  }

  @Nonnull
  @Override
  public String getItemStackDisplayName(@Nonnull ItemStack stack) {
    Material material = getMaterial(stack);

    String locString = getUnlocalizedName() + "." + material.getIdentifier();

    // custom name?
    if(I18n.canTranslate(locString)) {
      return Util.translate(locString);
    }

    // no, create the default name combo
    return material.getLocalizedItemName(super.getItemStackDisplayName(stack));
  }

  @Nonnull
  @SideOnly(Side.CLIENT)
  @Override
  public FontRenderer getFontRenderer(ItemStack stack) {
    return ClientProxy.fontRenderer;
  }

  @Override
  public boolean hasUseForStat(String stat) {
    for(ToolCore tool : TinkerRegistry.getTools()) {
      for(PartMaterialType pmt : tool.getRequiredComponents()) {
        if(pmt.isValidItem(this) && pmt.usesStat(stat)) {
          return true;
        }
      }
    }

    return false;
  }

  public boolean checkMissingMaterialTooltip(ItemStack stack, List<String> tooltip) {
    return checkMissingMaterialTooltip(stack, tooltip, null);
  }

  public boolean checkMissingMaterialTooltip(ItemStack stack, List<String> tooltip, String statIdentifier) {
    Material material = getMaterial(stack);

    if(material == Material.UNKNOWN) {
      NBTTagCompound tag = TagUtil.getTagSafe(stack);
      String materialID = tag.getString(Tags.PART_MATERIAL);

      String error;
      if(materialID != null && !materialID.isEmpty()) {
        error = I18n.translateToLocalFormatted("tooltip.part.missing_material", materialID);
      }
      else {
        error = I18n.translateToLocal("tooltip.part.missing_info");
      }
      tooltip.addAll(LocUtils.getTooltips(error));
      return true;
    }
    else if(statIdentifier != null && material.getStats(statIdentifier) == null) {
      tooltip.addAll(LocUtils.getTooltips(Util.translateFormatted("tooltip.part.missing_stats", material.getLocalizedName(), statIdentifier)));
      return true;
    }

    return false;
  }
}
