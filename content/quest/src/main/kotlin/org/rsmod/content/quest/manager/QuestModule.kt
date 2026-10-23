package org.rsmod.content.quest.manager

import org.rsmod.api.combat.commons.magic.SpellQuestRequirement
import org.rsmod.api.death.PlayerDeathDropLocationHook
import org.rsmod.api.player.hook.SpadeDigHook
import org.rsmod.content.generic.locs.bookcases.BookcaseSearchHook
import org.rsmod.content.quest.area.alkharid.princealirescue.DisguiseMakers
import org.rsmod.content.quest.area.alkharid.princealirescue.PrinceAliRescueQuest
import org.rsmod.content.quest.area.baxtorianfalls.waterfall.TouristCentreBookcases
import org.rsmod.content.quest.area.corsaircove.CorsairCurseQuest
import org.rsmod.content.quest.area.crandor.DragonSlayerQuest
import org.rsmod.content.quest.area.falador.doricsquest.DoricsQuest
import org.rsmod.content.quest.area.falador.knightssword.KnightsSwordQuest
import org.rsmod.content.quest.area.icemountain.BelowIceMountainQuest
import org.rsmod.content.quest.area.lumbridge.RuneMysteriesQuest
import org.rsmod.content.quest.area.lumbridge.idesofmilk.IdesOfMilkQuest
import org.rsmod.content.quest.area.lumbridge.xmarksthespot.XMarksTheSpotQuest
import org.rsmod.content.quest.area.portsarim.piratestreasure.PiratesTreasureQuest
import org.rsmod.content.quest.area.varrock.daddyshome.DaddysHomeQuest
import org.rsmod.content.quest.area.varrock.demonslayer.DemonSlayerQuest
import org.rsmod.content.quest.area.varrock.gertrudescat.GertrudesCatQuest
import org.rsmod.content.quest.area.varrock.shieldofarrav.ShieldOfArravBookcases
import org.rsmod.content.quest.area.varrock.shieldofarrav.ShieldOfArravQuest
import org.rsmod.plugin.module.PluginModule

public class QuestModule : PluginModule() {
    override fun bind() {
        bindInstance<QuestRequirementResolver>()
        bindInstance<RuneMysteriesQuest>()
        bindInstance<GertrudesCatQuest>()
        bindInstance<DaddysHomeQuest>()
        bindInstance<PrinceAliRescueQuest>()
        bindInstance<DisguiseMakers>()
        bindInstance<XMarksTheSpotQuest>()
        bindInstance<DoricsQuest>()
        bindInstance<IdesOfMilkQuest>()
        bindInstance<KnightsSwordQuest>()
        bindInstance<PiratesTreasureQuest>()
        bindInstance<DemonSlayerQuest>()
        bindInstance<ShieldOfArravQuest>()
        bindInstance<BelowIceMountainQuest>()
        bindInstance<QuestArena>()
        addSetBinding<PlayerDeathDropLocationHook>(QuestArena::class.java)
        bindInstance<CorsairCurseQuest>()
        bindInstance<DragonSlayerQuest>()
        addSetBinding<SpadeDigHook>(CorsairCurseQuest::class.java)
        addSetBinding<BookcaseSearchHook>(ShieldOfArravBookcases::class.java)
        addSetBinding<SpellQuestRequirement>(PolicySpellQuestRequirement::class.java)
        addSetBinding<BookcaseSearchHook>(TouristCentreBookcases::class.java)
        addSetBinding<SpadeDigHook>(XMarksTheSpotQuest::class.java)
        addSetBinding<SpadeDigHook>(PiratesTreasureQuest::class.java)
    }
}
