/*
 * Goety Tuner - 诡厄巫法附属Boss模组「调律师」 (Goety addon boss: The Tuner)
 *
 * Authors : toniat0 & vibe-coding
 * Team    : Goety Tuner Project (https://github.com/QieFanQie/)
 * License : MIT
 * Source  : https://github.com/QieFanQie/
 */

package com.tiaolvshi.goetytuner.combat;

import com.tiaolvshi.goetytuner.GoetyTuner;
import com.tiaolvshi.goetytuner.config.TunerCommonConfig;
import com.tiaolvshi.goetytuner.entity.TunerBoss;
import com.tiaolvshi.goetytuner.focus.FocusCategory;
import com.tiaolvshi.goetytuner.focus.FocusEntry;
import com.tiaolvshi.goetytuner.focus.FocusPoolManager;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.projectile.Projectile;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 召唤类聚晶动态评分追踪（二维：输出能力/生存能力）。
 *
 * 追踪以boss为主人的召唤物：
 * - 输出能力：召唤物为伤害来源的伤害事件求和平均
 * - 生存能力：存活时间 + 护甲值 + 生命值 + 药水效果 + 单次施法召唤数量 综合评分
 *
 * 与期望值比较后对聚晶评分加减偏移（不直接改静态评分）。
 */
public class SummonScoreTracker {

    private static class MinionRecord {
        final UUID minionId;
        final FocusEntry summonedBy;
        final long bornTick;
        float damageDealt;
        float maxHealth;
        double armor;
        int effectCount;
        boolean dead;
        long deathTick;

        MinionRecord(UUID minionId, FocusEntry summonedBy, long bornTick) {
            this.minionId = minionId;
            this.summonedBy = summonedBy;
            this.bornTick = bornTick;
        }
    }

    /** 全部在册召唤物 */
    private final Map<UUID, MinionRecord> minions = new HashMap<>();
    /** 满员标记（迟滞恢复：需低于 上限-hysteresis） */
    private boolean blockedFlag = false;

    // ================= 仆从登记 =================

    /**
     * 召唤法术结算后调用：扫描场上 owner==boss 且未登记的实体登记为该聚晶的召唤物。
     * （Goety的召唤法术统一以 setOwner 标记主人，见各SummonSpell实现）
     */
    public void registerNewMinions(ServerLevel level, TunerBoss boss, FocusEntry entry) {
        long now = level.getGameTime();
        for (Entity e : level.getAllEntities()) {
            if (e instanceof OwnableEntity ownable && ownable.getOwnerUUID() != null
                    && ownable.getOwnerUUID().equals(boss.getUUID())
                    && !minions.containsKey(e.getUUID())
                    && e.isAlive()) {
                MinionRecord rec = new MinionRecord(e.getUUID(), entry, now);
                if (e instanceof LivingEntity living) {
                    rec.maxHealth = living.getMaxHealth();
                    rec.armor = living.getArmorValue();
                    rec.effectCount = living.getActiveEffects().size();
                }
                minions.put(e.getUUID(), rec);
            }
        }
    }

    /** 场上存活仆从列表 */
    public List<Mob> ownedMinions(ServerLevel level, TunerBoss boss) {
        List<Mob> out = new ArrayList<>();
        for (Entity e : level.getAllEntities()) {
            if (e instanceof Mob mob && mob.isAlive()
                    && mob instanceof OwnableEntity own
                    && boss.getUUID().equals(own.getOwnerUUID())) {
                out.add(mob);
            }
        }
        return out;
    }

    public int countOwned(ServerLevel level, TunerBoss boss) {
        return ownedMinions(level, boss).size();
    }

    public boolean isBlockedFlag() {
        return blockedFlag;
    }

    public void setBlockedFlag(boolean v) {
        this.blockedFlag = v;
    }

    // ================= 伤害归因（召唤物输出） =================

    public void recordMinionDamage(LivingEntity victim, Entity directSource, float amount, TunerBoss boss) {
        if (amount <= 0) {
            return;
        }
        Entity attacker = directSource;
        if (attacker instanceof Projectile proj && proj.getOwner() != null) {
            attacker = proj.getOwner();
        }
        if (attacker == null) {
            return;
        }
        MinionRecord rec = minions.get(attacker.getUUID());
        if (rec != null) {
            rec.damageDealt += amount;
        }
    }

    // ================= 每tick结算 =================

    /** 每tick：清理死亡/卸载的召唤物并结算其评分贡献 */
    public void tick(ServerLevel level, TunerBoss boss, FocusPoolManager pools) {
        if (minions.isEmpty() || level.getGameTime() % 40 != 0) { // 2秒结算一次
            return;
        }
        long now = level.getGameTime();
        minions.entrySet().removeIf(e -> {
            MinionRecord rec = e.getValue();
            Entity ent = level.getEntity(rec.minionId);
            if (ent == null) {
                settleMinion(level, rec, boss, 0); // 卸载/消失按当期结算
                return true;
            }
            if (!ent.isAlive() && !rec.dead) {
                rec.dead = true;
                rec.deathTick = now;
                settleMinion(level, rec, boss, rec.deathTick - rec.bornTick);
                return true;
            }
            return false;
        });
    }

    /**
     * 结算单个召唤物 → 修正其聚晶的二维评分偏移。
     * 生存评分要素：存活tick、护甲、生命、有无增益效果（+单次召唤数量的均值隐含在多次结算中）
     * 输出评分要素：总伤害/存活秒数 = dps
     */
    private void settleMinion(ServerLevel level, MinionRecord rec, TunerBoss boss, long livedTicks) {
        double cap = TunerCommonConfig.DYNAMIC_SCORE_CAP.get();
        double rate = TunerCommonConfig.DPS_ADJUST_RATE.get();

        // --- 输出维度 ---
        double dps = rec.damageDealt / Math.max(1.0D, livedTicks / 20.0D);
        double dpsExpectation = 3.0D; // 期望dps基线（可配置化：TODO 配置项）
        double atkDelta = (dps / dpsExpectation - 1.0D) * rate;

        // --- 生存维度 ---
        double survPoints = rec.maxHealth / 20.0D      // 每20点血1分
                + rec.armor / 5.0D                     // 每5点甲1分
                + (rec.effectCount > 0 ? 1.0 : 0.0)    // 有药水效果+1
                + Math.min(10.0D, livedTicks / 200.0D); // 存活每10秒1分，上限10
        double survExpectation = 8.0D; // 期望生存分基线（可配置化）
        double survDelta = (survPoints / survExpectation - 1.0D) * rate;

        rec.summonedBy.addDynamicAttackOffset(atkDelta, cap);
        rec.summonedBy.addDynamicSurvivalOffset(survDelta, cap);
        GoetyTuner.LOGGER.debug("[Tuner] Minion settle for {} → atk{+}{}, surv{+}{}",
                rec.summonedBy.getItemId(),
                String.format("%.2f", atkDelta), String.format("%.2f", survDelta));
    }
}
