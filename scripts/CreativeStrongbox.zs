// Expert recipe for the Creative Strongbox (Thermal Expansion), proposed on r/feedthebeast:
// "Proposing an Expert Recipe for the Creative Strongbox" (thread 40w1nm).
// Needs MineTweaker 3 + ModTweaker 2 (Thaumcraft infusion) and Avaritia (Extreme Crafting).
// Requires the 8x compressed cobblestone recipe; the installer re-enables it in Expert mode.

#creative strongbox

val cc = <AWWayofTime:blockCrystal>;
val sm = <ThermalFoundation:Storage:5>;
val pm = <gendustry:PowerModule>;
val oc = <ExtraUtilities:cobblestone_compressed:7>;
val ii = <Avaritia:Resource:6>;
val ec = <extracells:storage.physical:3>;
val up = <SolarExpansion:solarPanelUltimate>;
val nb = <ForbiddenMagic:StarBlock>;
val cs = <DraconicEvolution:chaosShard>.reuse();
val cp = <ThermalExpansion:Cell>;
val ct = <ThermalExpansion:Tank>;
val gp = <Botania:pool:1>;
val ki = <ExtraUtilities:unstableingot:2>;
val sp = <DraconicEvolution:draconicDistructionStaff>;
val ni = <Avaritia:Resource:4>;
val lb = <BigReactors:BRMetalBlock:4>;
val sg = <appliedenergistics2:item.ItemMultiMaterial:47>;
val rc = <ExtraBees:honeyComb:21>;
val mb = <ExtraUtilities:pureLove>;
val ic = <Avaritia:Resource:5>;
val eo = <ForbiddenMagic:EldritchOrb>;
val pyro = <ThermalFoundation:bucket:3>;
val aero = <ThermalFoundation:bucket:7>;
val tect = <ThermalFoundation:bucket:8>;
val cryo = <ThermalFoundation:bucket:4>;
val ender = <ThermalFoundation:bucket:2>;
val glow = <ThermalFoundation:bucket:1>;
val red = <ThermalFoundation:bucket:0>;

val gcomb = <MagicBees:comb:14>;
val rcomb = <MagicBees:comb:23>;
val ocomb = <ExtraBees:honeyComb:30>;
val bcomb = <ExtraBees:honeyComb:85>;

// Infinity Catalyst: Ultimate Solar Panel, 7 Pure Love (Mobius), all Thermal Foundation liquids.
mods.thaumcraft.Infusion.addRecipe("RESEARCH",up,[mb,pyro,mb,aero,mb,tect,mb,cryo,mb,ender,mb,glow,mb,red],
"potentia 64, permutatio 64, vitreus 64, lux 64, auram 64",ic * 2,9);

// Neutronium Ingot: Ludicrite blocks around an AE2 singularity.
recipes.addShaped(ni,
[[lb, lb, lb],
 [lb, sg, lb],
 [lb, lb, lb]]);

// Infinity Ingot: combs around a Chaos Shard (the shard is not consumed).
recipes.addShaped(<Avaritia:Resource:6>,
[[rcomb, ocomb, gcomb],
 [bcomb,   cs , bcomb],
 [gcomb, ocomb, rcomb]]);

// Creative Strongbox on the Avaritia Extreme Crafting Table (9x9).
mods.avaritia.ExtremeCrafting.addShaped(<ThermalExpansion:Strongbox>,
[[oc,cc,cc,cc,sm,cc,cc,cc,oc],
 [cc,pm,ki,ki,ic,ki,ki,pm,cc],
 [cc,ii,ec,ec,ec,ec,ec,ii,cc],
 [cc,ii,ec,cp,ct,cp,ec,ii,cc],
 [sm,ni,ec,ct,gp,ct,ec,ni,sm],
 [cc,ii,ec,cp,ct,cp,ec,ii,cc],
 [cc,ii,ec,ec,ec,ec,ec,ii,cc],
 [cc,pm,ki,ki,ic,ki,ki,pm,cc],
 [oc,cc,cc,cc,sm,cc,cc,cc,oc]]
);
