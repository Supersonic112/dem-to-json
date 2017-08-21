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
    private List<FieldPath> fps_hero_gold, fps_hero_xp;
    private FieldPath posX, posY, maxHealth, health, ability_00, ability_level;
    
    JSONObject job, job_main;
    JSONArray replay_arr;
    
    public static void main(String[] args) throws Exception {
        new Main().run(args);
    }
   
    public void run(String[] args) throws Exception{
//Initialize timer, paths, runner and replay file        
        long tStart = System.currentTimeMillis();
//Source of replay and demo file
//        String source_path = args[0];
        String source_path = System.getProperty("user.dir") + "/../Replays/__raw_replays/2125002095.dem";
//        String dest_path = args[1];
//        String dest_path = System.getProperty("user.dir") + ".json";
        MappedFileSource mfs = new MappedFileSource(source_path);  
        this.s_runner = new SimpleRunner(mfs);
//Get Meta Info for replay, initialize Arrays and JSON Objects
//Initialize field path and utility Arrays / JSON Objects        
        this.hero_names_list = new ArrayList<String>();
        this.fps_hero_gold = new ArrayList<>();
        this.fps_hero_xp = new ArrayList<>();
        this.job_main = new JSONObject();
        this.replay_arr = new JSONArray();
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
//        try (FileWriter file = new FileWriter(dest_path)) {
//			file.write(this.job_main.toJSONString());
//			System.out.println("Successfully Copied JSON Object to File...");
//			System.out.println("\nJSON Object: " + this.job_main);
//		}
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
        }
    }
    
    private String getAttackerNameCompiled(CombatLogEntry cle) {
            return cle.getAttackerName() + (cle.isAttackerIllusion() ? " (illusion)" : "");
    }
    
    private String getCleanHeroName(String hero_name) {
        if (hero_name.startsWith("npc_dota") || hero_name.startsWith("CDOTA_Unit")) {
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
    int count = 0;
    
//    @UsesEntities
//    @OnTickStart
//    public void onTickStart(Context ctx, boolean synthetic) {
//        System.out.println(count++);
//    }
    
    
    @OnEntityUpdated
    public void onUpdated(Context ctx, Entity e, FieldPath[] updatedPaths, int updateCount) {
        
        
        if(this.isUsedEntity(e)) {
            JSONObject j_entry = new JSONObject();
            JSONObject j_entity = new JSONObject();

            String t_hero_name = "";
            int t_hero_gold_total = 0;
            int t_hero_xp_total = 0;
            
            int t_posX = 0;
            int t_posY = 0;
            int t_maxHealth = 0;
            int t_health = 0;
            
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
                            
                            this.replay_arr.add(j_entry);
                       
                            break;
                        } else if(updatedPaths[i].equals(this.fps_hero_xp.get(index))) {
                            t_hero_xp_total = e.getPropertyForFieldPath(this.fps_hero_xp.get(index));
                            t_hero_name = this.getCleanHeroName(this.hero_names_list.get(index));
                            j_entity.put("xp_total", t_hero_xp_total);
                            j_entity.put("hero_name", t_hero_name);
                            j_entry.put("tick", ctx.getTick());
                            j_entry.put("data", j_entity);
                            j_entry.put("type", "entity");
                            this.replay_arr.add(j_entry);
                            break;
                        }
                    }
                } // ***** END CDOTA_Data *****
            } else if (e.getDtClass().getDtName().startsWith("CDOTA_Unit_Hero")) {
                for (int i = 0; i < updateCount; i++) {
                    if (updatedPaths[i].equals(this.posX) || updatedPaths[i].equals(this.posY)) {
                        t_hero_name = this.getCleanHeroName(e.getDtClass().getDtName());
                        t_posX = e.getPropertyForFieldPath(this.posX);
                        t_posY = e.getPropertyForFieldPath(this.posY);
                        j_entity.put("posX", t_posX);
                        j_entity.put("posY", t_posY);
                        j_entity.put("hero_name", t_hero_name);
                        j_entry.put("data", j_entity);                            
                        j_entry.put("type", "entity");
                        j_entry.put("tick", ctx.getTick());
                        this.replay_arr.add(j_entry);
                        break;
                    }
                    else if (updatedPaths[i].equals(this.health) || updatedPaths[i].equals(this.maxHealth)) {
                        t_hero_name = this.getCleanHeroName(e.getDtClass().getDtName());
                        t_maxHealth = e.getPropertyForFieldPath(this.maxHealth);
                        t_health = e.getPropertyForFieldPath(this.health);
                        j_entity.put("maxHealth", t_maxHealth);
                        j_entity.put("health", t_health);
                        j_entity.put("hero_name", t_hero_name);
                        j_entry.put("data", j_entity);                            
                        j_entry.put("type", "entity");
                        j_entry.put("tick", ctx.getTick());
                        this.replay_arr.add(j_entry);
                        break;
                    }
                }
            }// ***** END CDOTA_Unit_Hero *****
        }else if (e.getDtClass().getDtName().startsWith("CDOTA_Ability")) {
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
        }      
        }// ***** END CDOTA_Ability *****
    }
  
//    @OnCombatLogEntry
//    public void onCombatLogEntry(Context ctx, CombatLogEntry cle) {
//        JSONObject j_entry = new JSONObject();
//        JSONObject j_entity = new JSONObject();
//
//        switch (cle.getType()) {
//            case DOTA_COMBATLOG_DAMAGE:
//                j_entry.put("tick", ctx.getTick());
//                j_entity.put("kind", "DAMAGE");
//                j_entity.put("attacker", getAttackerNameCompiled(cle));
//                j_entry.put("entity", j_entity);
//                this.replay_arr.add(j_entry);
//                break;
//            case DOTA_COMBATLOG_HEAL:
//                j_entry.put("tick", ctx.getTick());
//                j_entity.put("kind", "HEAL");
//                j_entry.put("entity", j_entity);
//                this.replay_arr.add(j_entry);
//                break;
//            case DOTA_COMBATLOG_MODIFIER_ADD:
//                j_entry.put("tick", ctx.getTick());
//                j_entity.put("kind", "MODIFIER_ADD");
//                j_entry.put("entity", j_entity);
//                this.replay_arr.add(j_entry);
//                break;
//            case DOTA_COMBATLOG_MODIFIER_REMOVE:
//                j_entry.put("tick", ctx.getTick());
//                j_entity.put("kind", "MODIFIER_REMOVE");
//                j_entry.put("entity", j_entity);
//                this.replay_arr.add(j_entry);
//                break;
//            case DOTA_COMBATLOG_ABILITY:
//                j_entry.put("tick", ctx.getTick());
//                j_entity.put("kind", "ABILITY");
//                j_entity.put("inflictor", cle.getInflictorName());
//                j_entity.put("ability_lvl", cle.getAbilityLevel());
//                j_entry.put("entity", j_entity);
//                this.replay_arr.add(j_entry);
//                break;
//            case DOTA_COMBATLOG_DEATH:
//                j_entry.put("tick", ctx.getTick());
//                j_entity.put("kind", "DEATH");
//                j_entry.put("entity", j_entity);
//                this.replay_arr.add(j_entry);
//                break;
//            case DOTA_COMBATLOG_ITEM:
//                j_entry.put("tick", ctx.getTick());
//                j_entity.put("kind", "ITEM");
//                j_entry.put("entity", j_entity);
//                this.replay_arr.add(j_entry);
//                break;
//            case DOTA_COMBATLOG_GOLD:
//                break;
//            case DOTA_COMBATLOG_XP:
//                break;
//            case DOTA_COMBATLOG_GAME_STATE:
//                break;
//            case DOTA_COMBATLOG_PURCHASE:
//                break;
//        }
//    }
