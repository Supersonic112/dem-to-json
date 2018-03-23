/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package de.peemoroyal.demtojson;

import java.io.FileWriter;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import skadistats.clarity.Clarity;
import skadistats.clarity.decoder.Util;
import skadistats.clarity.model.Entity;
import skadistats.clarity.model.FieldPath;
import skadistats.clarity.processor.entities.*;
import skadistats.clarity.processor.runner.Context;
import skadistats.clarity.processor.runner.ControllableRunner;
import skadistats.clarity.processor.runner.SimpleRunner;
import skadistats.clarity.source.MappedFileSource;
import skadistats.clarity.wire.common.proto.Demo;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import skadistats.clarity.model.CombatLogEntry;
import skadistats.clarity.processor.gameevents.OnCombatLogEntry;
import skadistats.clarity.processor.reader.OnTickStart;
/**
 *
 * @author petergleixner
 */
public class Main {
    
    private SimpleRunner s_runner;
    private List<String> hero_names_list;
    private List<FieldPath> fps_hero_gold, fps_hero_xp, fps_hero_mana;
    private FieldPath posX, posY, maxHealth, health, maxMana, mana, ability_00, ability_level, currentLevel,
    totalDamageTaken, currentXP, lifeState, manaRegen, healthRegen, moveSpeed, physicalArmor, magicalResistance,
    strength, strengthTotal, agility, agilityTotal, intellect, intellectTotal, recentDamage;
    
    JSONObject job, job_main;
    JSONArray replay_arr;
    // to check for and prevent duplicate messages:
    private JSONObject j_prev_entity;
    private JSONObject j_prev_gold;
    private JSONObject j_prev_gold_xp;
    
    public static void main(String[] args) throws Exception {
        new Main().run(args);
    }
   
    public void run(String[] args) throws Exception{
//Initialize timer, paths, runner and replay file
        long tStart = System.currentTimeMillis();
//Source of replay and demo file
        String source_path = args[0];
//        String source_path = System.getProperty("user.dir") + "/../Replays/__raw_replays/rep_s02_03.dem";
        String dest_path = args[1];
//        String dest_path = System.getProperty("user.dir") + ".json";
        MappedFileSource mfs = new MappedFileSource(source_path);  
        this.s_runner = new SimpleRunner(mfs);
//Get Meta Info for replay, initialize Arrays and JSON Objects
//Initialize field path and utility Arrays / JSON Objects        
        this.hero_names_list = new ArrayList<String>();
        this.fps_hero_gold = new ArrayList<>();
        this.fps_hero_xp = new ArrayList<>();
        this.fps_hero_mana = new ArrayList<>();
        this.job_main = new JSONObject();
        this.replay_arr = new JSONArray();
        this.j_prev_entity = new JSONObject();
        this.j_prev_gold_xp = new JSONObject();
//Demoinfo: get Heronames from Demoinfo and their ID (0-4 Dire, 5-9 Radiant), convert to JSON
        Demo.CDemoFileInfo info = Clarity.infoForFile(source_path);

//        First idea for the easiest way to map the hero name to the player idea. Must be a better solution
        for (Demo.CGameInfo.CDotaGameInfo.CPlayerInfo playerInfo : info.getGameInfo().getDota().getPlayerInfoList()) {
            this.hero_names_list.add(this.getCleanHeroName(playerInfo.getHeroName()));
        }
//Start parsing
        s_runner.runWith(this);
//Put the constructed Array in Structure, print out JSON file
        this.job_main.put("meta_info", this.infoToJSON(info));
        this.job_main.put("replay", replay_arr);
        try (FileWriter file = new FileWriter(dest_path)) {
			file.write(this.job_main.toJSONString());
			System.out.println("Successfully Copied JSON Object to File...");
			//System.out.println("\nJSON Object: " + this.job_main);
		}
        long tMatch = System.currentTimeMillis() - tStart;
        System.out.println("Time taken: " + tMatch);

        System.out.println(this.infoToJSON(info));

    }
    
    private JSONObject infoToJSON(Demo.CDemoFileInfo info){
        Demo.CGameInfo.CDotaGameInfo game_info = info.getGameInfo().getDota();
        JSONObject info_object = new JSONObject();
        JSONObject game_info_object = new JSONObject();
        JSONArray player_info_arr = new JSONArray();
        JSONObject player_info_object;
        
        for (Demo.CGameInfo.CDotaGameInfo.CPlayerInfo playerInfo : game_info.getPlayerInfoList()) {
            player_info_object = new JSONObject();
            
            player_info_object.put("hero_name", (this.getCleanHeroName(playerInfo.getHeroName())));
            player_info_object.put("steamid", playerInfo.getSteamid());
            player_info_object.put("game_team", playerInfo.getGameTeam());
            player_info_arr.add(player_info_object);
        }
        
        game_info_object.put("match_id", game_info.getMatchId());
        game_info_object.put("game_mode", game_info.getGameMode());
        game_info_object.put("game_winner", game_info.getGameWinner());
        game_info_object.put("radiant_id", game_info.getRadiantTeamId());
        game_info_object.put("dire_id", game_info.getDireTeamId());
        
        info_object.put("playback_time", info.getPlaybackTime());
        info_object.put("playback_ticks", info.getPlaybackTicks());
        info_object.put("game_info", game_info_object);
        info_object.put("player_info", player_info_arr);

        return info_object;
    }
    
    
    private boolean isUsedEntity(Entity e){
        boolean isUsedEntity = 
               e.getDtClass().getDtName().startsWith("CDOTA_Unit_Hero") ||
               e.getDtClass().getDtName().startsWith("CDOTA_DataDire") ||
               e.getDtClass().getDtName().startsWith("CDOTA_DataRadiant");
//               (e.getDtClass().getDtName().startsWith("CDOTA_Ability") && !e.getDtClass().getDtName().startsWith("CDOTA_Ability_AttributeBonus"))

               
        return isUsedEntity;
    }
     
    private void mapIndicesToHeroes(int index, Entity e) {
        String[] properties = {
            "m_vecDataTeam.%i.m_iTotalEarnedGold",
            "m_vecDataTeam.%i.m_iTotalEarnedXP"
        };
        this.fps_hero_gold.add(e.getDtClass().getFieldPathForName(properties[0].replaceAll("%i", Util.arrayIdxToString(index))));
        this.fps_hero_xp.add(e.getDtClass().getFieldPathForName(properties[1].replaceAll("%i", Util.arrayIdxToString(index))));
        this.fps_hero_mana.add(e.getDtClass().getFieldPathForName(properties[1].replaceAll("%i", Util.arrayIdxToString(index))));
    }
    
    private void ensureFieldPaths(Entity e){
        if (e.getDtClass().getDtName().startsWith("CDOTA_Data")) {
            for (int i = 0; i<5; i++){
                this.mapIndicesToHeroes(i, e);
            }
        }
        
        if(posX == null || posY == null) {
            this.posX = e.getDtClass().getFieldPathForName("CBodyComponent.m_cellX");
            this.posY = e.getDtClass().getFieldPathForName("CBodyComponent.m_cellY");
            this.maxHealth = e.getDtClass().getFieldPathForName("m_iMaxHealth");
            this.health = e.getDtClass().getFieldPathForName("m_iHealth");
            this.maxMana = e.getDtClass().getFieldPathForName("m_flMaxMana");
            this.mana = e.getDtClass().getFieldPathForName("m_flMana");
            this.currentLevel = e.getDtClass().getFieldPathForName("m_iCurrentLevel");
            this.totalDamageTaken = e.getDtClass().getFieldPathForName("m_nTotalDamageTaken");
            this.currentXP = e.getDtClass().getFieldPathForName("m_iCurrentXP");
            this.lifeState = e.getDtClass().getFieldPathForName("m_lifeState");
            this.manaRegen = e.getDtClass().getFieldPathForName("m_flManaThinkRegen");
            this.healthRegen = e.getDtClass().getFieldPathForName("m_flHealthThinkRegen");
            this.moveSpeed = e.getDtClass().getFieldPathForName("m_iMoveSpeed");
            this.physicalArmor = e.getDtClass().getFieldPathForName("m_flPhysicalArmorValue");
            this.magicalResistance = e.getDtClass().getFieldPathForName("m_flMagicalResistanceValue");
            this.strength = e.getDtClass().getFieldPathForName("m_flStrength");
            this.strengthTotal = e.getDtClass().getFieldPathForName("m_flStrengthTotal");
            this.agility = e.getDtClass().getFieldPathForName("m_flAgility");
            this.agilityTotal = e.getDtClass().getFieldPathForName("m_flAgilityTotal");
            this.intellect = e.getDtClass().getFieldPathForName("m_flIntellect");
            this.intellectTotal = e.getDtClass().getFieldPathForName("m_flIntellectTotal");
            this.recentDamage = e.getDtClass().getFieldPathForName("m_iRecentDamage");
        }
    }
    
    private String getAttackerNameCompiled(CombatLogEntry cle) {
            return cle.getAttackerName() + (cle.isAttackerIllusion() ? " (illusion)" : "");
    }
    
    private String getCleanHeroName(String hero_name) {
        if (hero_name.startsWith("npc_dota_hero") || hero_name.startsWith("CDOTA_Unit")) {
           hero_name = hero_name.replaceAll("_", "").toLowerCase().split("hero")[1];
        }
        return hero_name;
    }
    
    @OnEntityCreated
    public void onCreated(Context ctx, Entity e) {
        if(isUsedEntity(e)){
            ensureFieldPaths(e);
        }
    }
    
    @OnEntityUpdated
    public void onUpdated(Context ctx, Entity e, FieldPath[] updatedPaths, int updateCount) {
        
        
        if(this.isUsedEntity(e)) {
            JSONObject j_entry = new JSONObject();
            JSONObject j_entity = new JSONObject();

            String t_hero_name = "";
            int t_hero_gold_total = 0;
            int t_hero_xp_total = 0;
            int t_hero_current_xp = 0; // an alternative to the count above
            int t_hero_current_level = 0;
            
            int t_posX = 0;
            int t_posY = 0;
            int t_maxHealth = 0;
            int t_health = 0;
            float t_healthRegen = 0;
            float t_maxMana = 0;
            float t_mana = 0;
            float t_manaRegen = 0;
            long t_totalDamageTaken = 0;
            int t_lifeState = 0;
            //int t_moveSpeed = 0;
            float t_physicalArmorValue = 0;
            float t_magicalResistanceValue = 0;
            float t_strength = 0;
            float t_strengthTotal = 0;
            float t_agility = 0;
            float t_agilityTotal = 0;
            float t_intellect = 0;
            float t_intellectTotal = 0;
            int t_recentDamage = 0;
            
            if (e.getDtClass().getDtName().startsWith("CDOTA_Data")) {
                int index = 0;
                for(int i = 0; i < updateCount; i++) {
                    for(int j = 0; j < 5; j++ ){
                        index = j;
                        if(e.getDtClass().getDtName().startsWith("CDOTA_DataDire")){
                            index = j+5;
                        }
    
                        if (updatedPaths[i].equals(this.fps_hero_gold.get(index))) {
                            t_hero_gold_total = e.getPropertyForFieldPath(this.fps_hero_gold.get(index));
                            t_hero_name = this.getCleanHeroName(this.hero_names_list.get(index));
                            j_entity.put("gold_total", t_hero_gold_total);
                            j_entity.put("hero_name", t_hero_name);
                            j_entry.put("tick", ctx.getTick());
                            j_entry.put("data", j_entity);
                            j_entry.put("type", "entity");
                        }
                        if(updatedPaths[i].equals(this.fps_hero_xp.get(index))) {
                            t_hero_xp_total = e.getPropertyForFieldPath(this.fps_hero_xp.get(index));
                            t_hero_name = this.getCleanHeroName(this.hero_names_list.get(index));
                            j_entity.put("xp_total", t_hero_xp_total);
                            j_entity.put("hero_name", t_hero_name);
                            j_entry.put("tick", ctx.getTick());
                            j_entry.put("data", j_entity);
                            j_entry.put("type", "entity");
                        }
                        if (!j_entity.equals(j_prev_gold_xp) && !j_entry.isEmpty()) {
                        	// visible changes to the entry/entity, add element
                        	j_prev_gold_xp = j_entity;
                        	this.replay_arr.add(j_entry);
                        }
                        j_entity = new JSONObject();
                        j_entry = new JSONObject();
                    }
                } // ***** END CDOTA_Data *****
            } else if (e.getDtClass().getDtName().startsWith("CDOTA_Unit_Hero")) {
                for (int i = 0; i < updateCount; i++) {
                    if (updatedPaths[i].equals(this.posX) || updatedPaths[i].equals(this.posY)) {
                        t_posX = e.getPropertyForFieldPath(this.posX);
                        t_posY = e.getPropertyForFieldPath(this.posY);
                        j_entity.put("posX", t_posX);
                        j_entity.put("posY", t_posY);
                        j_entry.put("data", j_entity);
                        j_entry.put("tick", ctx.getTick());
                    }
                    if (updatedPaths[i].equals(this.health) || updatedPaths[i].equals(this.mana)) {
                        t_maxHealth = e.getPropertyForFieldPath(this.maxHealth);
                        t_health = e.getPropertyForFieldPath(this.health);
                        t_maxMana = e.getPropertyForFieldPath(this.maxMana);
                        t_mana = e.getPropertyForFieldPath(this.mana);
                        t_lifeState = e.getPropertyForFieldPath(this.lifeState);
                        j_entity.put("maxHealth", t_maxHealth);
                        j_entity.put("health", t_health);
                        j_entity.put("maxMana", (int)t_maxMana);
                        j_entity.put("mana", (int)t_mana);
                        j_entity.put("lifeState", t_lifeState);
                        t_manaRegen = e.getPropertyForFieldPath(this.manaRegen);
                        t_healthRegen = e.getPropertyForFieldPath(this.healthRegen);
                    	j_entity.put("manaRegen", (int) t_manaRegen);
                        j_entity.put("healthRegen", (int) t_healthRegen);
                    }
                    if (updatedPaths[i].equals(this.agilityTotal) || updatedPaths[i].equals(this.intellectTotal) ||
                    		updatedPaths[i].equals(this.strengthTotal)) {
                    	// this is a bit messy, but occasionally the JVM fails to convert integers to strings
                    	// and the try/catch blocks are a simple workaround
                    	try {
                    		t_agility = (float) e.getPropertyForFieldPath(this.agility);
                    	} catch (ClassCastException exc) {
							int temp = e.getPropertyForFieldPath(this.agility);
	                        t_agility = (float) temp;
						}
                    	try {
                    		t_agilityTotal = (float) e.getPropertyForFieldPath(this.agilityTotal);
	                    } catch (ClassCastException exc) {
							int temp = e.getPropertyForFieldPath(this.agilityTotal);
	                        t_agilityTotal = (float) temp;
						}
                    	try {
                    		t_intellect = (float) e.getPropertyForFieldPath(this.intellect);
                    	} catch (ClassCastException exc) {
							int temp = e.getPropertyForFieldPath(this.intellect);
	                        t_intellect = (float) temp;
						}
                        try {
                        	t_intellectTotal = (float) e.getPropertyForFieldPath(this.intellectTotal);
                        } catch (ClassCastException exc) {
							int temp = e.getPropertyForFieldPath(this.intellectTotal);
	                        t_intellectTotal = (float) temp;
						}
                        try {
                        	t_strength = (float) e.getPropertyForFieldPath(this.strength);
                        } catch (ClassCastException exc) {
							int temp = e.getPropertyForFieldPath(this.strength);
	                        t_strength = (float) temp;
						}
                        try {
                        	t_strengthTotal = (float) e.getPropertyForFieldPath(this.strengthTotal);
                        } catch (ClassCastException exc) {
							int temp = e.getPropertyForFieldPath(this.strengthTotal);
	                        t_strengthTotal = (float) temp;
						}
                        j_entity.put("agility", t_agility);
                        j_entity.put("agilityTotal", t_agilityTotal);
                        j_entity.put("intellect", t_intellect);
                        j_entity.put("intellectTotal", t_intellectTotal);
                        j_entity.put("strength", t_strength);
                        j_entity.put("strengthTotal", t_strengthTotal);
                    }
//                    if (updatedPaths[i].equals(this.moveSpeed)) { // unfortunately static over the whole game
//                        t_moveSpeed = e.getPropertyForFieldPath(this.moveSpeed);
//                        j_entity.put("moveSpeed", t_moveSpeed);
//                    }
                    if (updatedPaths[i].equals(this.currentLevel)) {
                        t_hero_current_level = e.getPropertyForFieldPath(this.currentLevel);
                        j_entity.put("current_level", t_hero_current_level);
                    }
                    if (updatedPaths[i].equals(this.physicalArmor)||updatedPaths[i].equals(this.magicalResistance)) {
                    	t_magicalResistanceValue = e.getPropertyForFieldPath(this.magicalResistance);
                        t_physicalArmorValue = e.getPropertyForFieldPath(this.physicalArmor);
                    	j_entity.put("magicalResistance", t_magicalResistanceValue);
                        j_entity.put("physicalArmor", t_physicalArmorValue);
                    }
                    if (updatedPaths[i].equals(this.totalDamageTaken)||updatedPaths[i].equals(this.recentDamage)) {
                    	t_totalDamageTaken = e.getPropertyForFieldPath(this.totalDamageTaken);
                        t_recentDamage = e.getPropertyForFieldPath(this.recentDamage);
                    	j_entity.put("totalDamageTaken", t_totalDamageTaken);
                        j_entity.put("recentDamage", t_recentDamage);
                    }
                    if (j_entity.isEmpty()) {
                    	break;
                    }
                    t_hero_name = this.getCleanHeroName(e.getDtClass().getDtName());
                    j_entity.put("hero_name", t_hero_name);
                    if (j_entity.equals(j_prev_entity)) {
                    	// no visible changes to the entry/entity, ignore element
                    	break;
                    }
                    j_prev_entity = j_entity;
                    j_entry.put("type", "entity");
                    j_entry.put("data", j_entity);
                    j_entry.put("tick", ctx.getTick());
                    this.replay_arr.add(j_entry);
                }// ***** END CDOTA_Unit_Hero *****
            }
        }//else if (e.getDtClass().getDtName().startsWith("CDOTA_Ability")) {
//            m_flCooldownLength
//            this.ability_00 = e.getDtClass().getFieldPathForName("m_hAbilities.0000");
//            int id_00 = e.getPropertyForFieldPath(this.ability_00);
//           
//            if (e.getDtClass().getDtName().startsWith("CDOTA_Abil")) {
//                Entity ability_ent = ctx.getProcessor(Entities.class).getByHandle(id);
//                this.ability_level = e.getDtClass().getFieldPathForName("m_iLevel");
//                int ability = e.getPropertyForFieldPath(this.ability_level);
//
//                for(int i = 0; i < updateCount; i++) {
//                    if (updatedPaths[i].equals(this.ability_level)) {
//
//
//
//                        System.out.println("Tick: " + ctx.getTick());
//                        System.out.println("hero: " + e.getDtClass().getDtName());
//                        System.out.println("abilitiy: " + e.getDtClass().getDtName());
//                        System.out.println("ab_lvl: " + ability);
//                        System.out.println("------------------------------------------");
//                        break;
//                    }
//                }
//            }
        //}
    }// ***** END CDOTA_Ability *****

    @OnCombatLogEntry
    public void onCombatLogEntry(Context ctx, CombatLogEntry cle) {
        JSONObject j_entry = new JSONObject();
        JSONObject j_entity = new JSONObject();
//        if (!cle.isTargetHero()) {
//        	// non-hero targets are not of interest for me
//        	return;
//        }
        j_entry.put("type", "combatlog");
        //System.out.println("received other message id: "+cle.getType());
        switch (cle.getType()) {
            case DOTA_COMBATLOG_DAMAGE:
            	if (!cle.isTargetHero()) {
                	// non-hero targets are not of interest for me
                	return;
                }
                j_entry.put("tick", ctx.getTick());
                j_entity.put("attacker", this.getCleanHeroName(cle.getAttackerName()));
                j_entity.put("target", this.getCleanHeroName(cle.getTargetName()));
                j_entity.put("value", cle.getValue());
                j_entry.put("data", j_entity);
                j_entry.put("type", "damage");
                this.replay_arr.add(j_entry);
                break;
            case DOTA_COMBATLOG_HEAL:
            	if (!cle.isTargetHero()) {
                	// non-hero targets are not of interest for me
                	return;
                }
                j_entry.put("tick", ctx.getTick());
                j_entity.put("value", cle.getValue());
                j_entity.put("healer", this.getCleanHeroName(cle.getAttackerName()));
                j_entity.put("healed", this.getCleanHeroName(cle.getTargetName()));
                j_entry.put("data", j_entity);
                j_entry.put("type", "heal");
                this.replay_arr.add(j_entry);
                break;
            case DOTA_COMBATLOG_MODIFIER_ADD:
            	if (!cle.isTargetHero()) {
                	// non-hero targets are not of interest for me
                	return;
                }
            	if (cle.getValue() == 0) { // magic value, don't ask why; attackerTeam/targetTeam are not valid
            		// assume buff
                    j_entity.put("buffer", this.getCleanHeroName(cle.getAttackerName()));
                    j_entry.put("type", "buff");
            	} else {
            		// assume debuff
                    j_entity.put("debuffer", this.getCleanHeroName(cle.getAttackerName()));
                    j_entry.put("type", "debuff");
            	}
                j_entry.put("tick", ctx.getTick());
                j_entity.put("target", this.getCleanHeroName(cle.getTargetName()));
                j_entity.put("buffname", cle.getInflictorName());
                j_entry.put("data", j_entity);
                this.replay_arr.add(j_entry);
                break;
            case DOTA_COMBATLOG_MODIFIER_REMOVE:
            	if (!cle.isTargetHero()) {
                	// non-hero targets are not of interest for me
                	return;
                }
                j_entry.put("tick", ctx.getTick());
                j_entity.put("target", this.getCleanHeroName(cle.getTargetName()));
                j_entity.put("buff_name", cle.getInflictorName());
                j_entry.put("data", j_entity);
                j_entry.put("type", "modifier_remove");
                this.replay_arr.add(j_entry);
                break;
            case DOTA_COMBATLOG_ABILITY:
            	if (!cle.isTargetHero()) {
                	// non-hero targets are not of interest for me
                	return;
                }
                j_entry.put("tick", ctx.getTick());
                j_entity.put("attacker", this.getCleanHeroName(cle.getAttackerName()));
                j_entity.put("target", this.getCleanHeroName(cle.getTargetName()));
                j_entity.put("ability_name", cle.getInflictorName());
                j_entity.put("ability_lvl", cle.getAbilityLevel());
                j_entry.put("data", j_entity);
                j_entry.put("type", "ability");
                this.replay_arr.add(j_entry);
                break;
            case DOTA_COMBATLOG_DEATH:
            	if (!cle.isTargetHero()) {
                	// non-hero targets are not of interest for me
                	return;
                }
                j_entry.put("tick", ctx.getTick());
                j_entity.put("attacker", this.getCleanHeroName(cle.getAttackerName()));
                List<Integer> assist_players = cle.getAssistPlayers();
                List<String> assist_player_names = new ArrayList<String>(4); // there can be 4 assistants at most
                for (int i = 0; i < assist_players.size(); i++) {
            		assist_player_names.add(this.hero_names_list.get(assist_players.get(i)));
                }
                j_entity.put("assistants", assist_player_names);
                j_entity.put("killed", this.getCleanHeroName(cle.getTargetName()));
                j_entry.put("data", j_entity);
                j_entry.put("type", "death");
                this.replay_arr.add(j_entry);
                break;
            case DOTA_COMBATLOG_ITEM:
                j_entry.put("tick", ctx.getTick());
                j_entity.put("user", this.getCleanHeroName(cle.getAttackerName()));
                j_entity.put("target", this.getCleanHeroName(cle.getTargetName()));
                j_entity.put("item_name", this.getCleanHeroName(cle.getInflictorName()));
                j_entry.put("data", j_entity);
                j_entry.put("type", "item");
                this.replay_arr.add(j_entry);
                break;
            case DOTA_COMBATLOG_GOLD:
            	j_entry.put("tick", ctx.getTick());
            	j_entity.put("value", Math.abs(cle.getValue()));
            	j_entity.put("hero", this.getCleanHeroName(cle.getTargetName()));
                j_entry.put("type", "gold");
            	j_entry.put("data", j_entity);
                break;
            case DOTA_COMBATLOG_XP:
            	j_entry.put("tick", ctx.getTick());
            	j_entity.put("hero_name", this.getCleanHeroName(cle.getTargetName()));
            	j_entity.put("hero_level", cle.getTargetHeroLevel());
            	j_entity.put("value", cle.getValue());
                j_entry.put("type", "xp");
            	j_entry.put("data", j_entity);
                break;
            case DOTA_COMBATLOG_HERO_SAVED: // unfortunately not active
            	System.out.println("hero saved");
            	j_entry.put("tick", ctx.getTick());
            	j_entity.put("hero_name", this.getCleanHeroName(cle.getTargetName()));
                j_entry.put("type", "saved");
            	j_entry.put("data", j_entity);
            	break;
            case DOTA_COMBATLOG_HERO_LEVELUP: // unfortunately not active
            	System.out.println("levelup");
            	j_entry.put("tick", ctx.getTick());
            	j_entity.put("hero_name", this.getCleanHeroName(cle.getTargetName()));
            	j_entity.put("hero_level", cle.getTargetHeroLevel());
                j_entry.put("type", "levelup");
            	j_entry.put("data", j_entity);
                break;
            case DOTA_COMBATLOG_LOCATION: // unfortunately not active
            	j_entry.put("tick", ctx.getTick());
            	j_entity.put("location", cle.getEventLocation());
            	j_entity.put("hero_name", this.getCleanHeroName(cle.getTargetName()));
                j_entry.put("type", "location");
            	j_entry.put("data", j_entity);
            	break;
            //case DOTA_COMBATLOG_GAME_STATE:
            //    break;
            case DOTA_COMBATLOG_PURCHASE:
            	j_entry.put("tick", ctx.getTick());
            	j_entity.put("item_name", cle.getEventLocation());
            	j_entity.put("hero_name", this.getCleanHeroName(cle.getTargetName()));
                j_entry.put("type", "purchase");
            	j_entry.put("data", j_entity);
            	break;
            default:
            	System.out.println("received other message id: "+cle.getType());
            	break;
        }
    }
}
