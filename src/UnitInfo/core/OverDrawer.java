package UnitInfo.core;

import UnitInfo.ui.*;
import arc.*;
import arc.audio.Sound;
import arc.graphics.*;
import arc.graphics.g2d.*;
import arc.graphics.gl.*;
import arc.math.*;
import arc.math.geom.*;
import arc.scene.ui.layout.*;
import arc.struct.*;
import arc.util.*;
import mindustry.*;
import mindustry.ai.*;
import mindustry.ai.types.*;
import mindustry.content.*;
import mindustry.core.*;
import mindustry.entities.units.*;
import mindustry.game.*;
import mindustry.gen.*;
import mindustry.graphics.*;
import mindustry.logic.*;
import mindustry.ui.*;
import mindustry.world.*;
import mindustry.world.blocks.defense.*;
import mindustry.world.blocks.defense.turrets.*;
import mindustry.world.blocks.distribution.*;
import mindustry.world.blocks.payloads.*;
import mindustry.world.blocks.power.*;
import mindustry.world.blocks.storage.*;
import mindustry.world.blocks.units.*;

import java.util.Objects;

import static UnitInfo.SVars.*;
import static arc.Core.*;
import static mindustry.Vars.*;

public class OverDrawer {
    public static Teamc target;
    public static Seq<MassDriver.MassDriverBuild> linkedMasses = new Seq<>();
    public static Seq<PayloadMassDriver.PayloadDriverBuild> linkedPayloadMasses = new Seq<>();
    public static Seq<Building> linkedNodes = new Seq<>();
    public static Seq<Building> linkedBuilds = new Seq<>();
    public static Seq<Tile> pathTiles = new Seq<>();
    public static FrameBuffer effectBuffer = new FrameBuffer();
    public static int otherCores;
    public static boolean locked;
    public static ObjectMap<Team, Seq<Building>> tmpbuildobj = new ObjectMap<>();

    static float sin = Mathf.absin(Time.time, 6f, 1f);

    public static void setEvent(){
        Events.run(EventType.Trigger.draw, () -> {


            effectBuffer.resize(graphics.getWidth(), graphics.getHeight());
            Draw.drawRange(158, 1f, () -> effectBuffer.begin(Color.clear), () -> {
                effectBuffer.end();
                effectBuffer.blit(lineShader);
            });

            Draw.z(158);

            int[] paths = {0};
            int[] units = {0};
            int[] logics = {0};
            if(unitLine || logicLine) Groups.unit.each(u -> {
                UnitController c = u.controller();
                UnitCommand com = u.team.data().command;

                if(c instanceof LogicAI ai &&
                        logics[0] <= settings.getInt("logiclinelimit") &&
                        logicLine && (ai.control == LUnitControl.approach || ai.control == LUnitControl.move)) {
                    logics[0]++;
                    Lines.stroke(1, u.team.color);
                    Lines.line(u.x(), u.y(), ai.moveX, ai.moveY);
                    Lines.stroke(0.5f + Mathf.absin(6f, 0.5f), Tmp.c1.set(Pal.logicOperations).lerp(Pal.sap, Mathf.absin(6f, 0.5f)));
                    Lines.line(u.x(), u.y(), ai.controller.x, ai.controller.y);
                }

                if(units[0] > settings.getInt("unitlinelimit") || //prevent lag
                        !unitLine || //disabled
                        u.type.flying || //not flying
                        c instanceof MinerAI || //not mono
                        c instanceof BuilderAI || //not poly
                        c instanceof RepairAI || //not mega
                        c instanceof DefenderAI || //not oct
                        c instanceof FormationAI || //not commanded unit
                        c instanceof FlyingAI || //not flying anyway
                        com == UnitCommand.idle) return; //not idle
                units[0]++;
                otherCores = Groups.build.count(b -> b instanceof CoreBlock.CoreBuild && b.team != u.team);
                int pathType = u.pathType();
                if(u.controller() instanceof SuicideAI) pathType = 0;
                getNextTile(u.tileOn(), pathType, u.team, com.ordinal());
                pathTiles.filter(Objects::nonNull);
                for(int i = 1; i < pathTiles.size; i++) {
                    if(i + 1 >= pathTiles.size) continue; //prevent IndexOutException
                    Tile tile1 = pathTiles.get(i);
                    Tile tile2 = pathTiles.get(i + 1);
                    Lines.stroke(1, u.team.color);
                    Lines.line(tile1.worldx(), tile1.worldy(), tile2.worldx(), tile2.worldy());
                }
                pathTiles.clear();
            });

            if(pathLine) spawner.getSpawns().each(t -> {
                if(paths[0] > settings.getInt("pathlinelimit")) return;
                paths[0]++;
                Team enemyTeam = state.rules.waveTeam;
                for(int p = 0; p < (Vars.state.rules.spawns.count(g->g.type.naval)>0?3:2); p++) {
                    otherCores = Groups.build.count(b -> b instanceof CoreBlock.CoreBuild && b.team != enemyTeam);

                    getNextTile(t, p, enemyTeam, Pathfinder.fieldCore);
                    pathTiles.filter(Objects::nonNull);
                    for(int i = 1; i < pathTiles.size; i++) {
                        if(i + 1 >= pathTiles.size) continue; //prevent IndexOutException
                        Tile tile1 = pathTiles.get(i);
                        Tile tile2 = pathTiles.get(i + 1);
                        Lines.stroke(1, enemyTeam.color);
                        Lines.line(tile1.worldx(), tile1.worldy(), tile2.worldx(), tile2.worldy());
                    }
                    pathTiles.clear();
                }
            });
            Draw.reset();
            Draw.z(Layer.overlayUI);

            int[] arrows = {0};
            if(settings.getBool("spawnerarrow")) spawner.getSpawns().each(t -> {
                if(arrows[0] > settings.getInt("spawnarrowlimit")) return;
                arrows[0]++;
                float leng = (player.unit() != null && player.unit().hitSize > 4 * 8f ? player.unit().hitSize * 1.5f : 4 * 8f) + sin;
                float camx = player.unit() != null ? player.unit().x : camera.position.x;
                float camy = player.unit() != null ? player.unit().y : camera.position.y;
                Lines.stroke(1f + sin/2, Pal.accent);
                Lines.circle(camx, camy, leng - 4f);
                Drawf.arrow(camx, camy, t.worldx(), t.worldy(), leng, (Math.min(200 * 8f, Mathf.dst(camx, camy, t.worldx(), t.worldy())) / (200 * 8f)) * (5f + sin));
            });

            if(Core.settings.getBool("unithealthui")) {
                Groups.unit.each(FreeBar::draw);
            }

            if(Core.settings.getBool("blockfont")) {
                indexer.eachBlock(null, camera.position.x, camera.position.y, 400, b -> true, b -> {
                    if(b instanceof ForceProjector.ForceBuild force) {
                        ForceProjector forceBlock = (ForceProjector) force.block;
                        float max = forceBlock.shieldHealth + forceBlock.phaseShieldBoost * force.phaseHeat;

                        Fonts.outline.draw((int)b.health + " / " + (int)b.maxHealth,
                                b.x, b.y - b.block.size * 8 * 0.25f - 2,
                                Tmp.c1.set(Pal.items).lerp(Pal.health, 1-b.healthf()), (b.block.size == 1 ? 0.3f : 0.25f) * 0.25f * b.block.size, false, Align.center);
                        Fonts.outline.draw((int)(max-force.buildup) + " / " + (int)max,
                                b.x, b.y - b.block.size * 8 * 0.25f + 2,
                                Tmp.c1.set(Pal.shield).lerp(Pal.gray, 1-((max-force.buildup) / max)), (b.block.size == 1 ? 0.3f : 0.25f) * 0.25f * b.block.size, false, Align.center);
                    }
                    else if(b instanceof ReloadTurret.ReloadTurretBuild || b instanceof UnitFactory.UnitFactoryBuild || b instanceof Reconstructor.ReconstructorBuild) {
                        float progress = 0f;
                        if(b instanceof ReloadTurret.ReloadTurretBuild turret) progress = turret.reload / ((ReloadTurret)turret.block).reloadTime * 100;
                        if(b instanceof UnitFactory.UnitFactoryBuild factory) progress = factory.fraction() * 100;
                        if(b instanceof Reconstructor.ReconstructorBuild reconstructor) progress = reconstructor.fraction() * 100;

                        Fonts.outline.draw((int)b.health + " / " + (int)b.maxHealth,
                                b.x, b.y - b.block.size * 8 * 0.25f - 2,
                                Tmp.c1.set(Pal.items).lerp(Pal.health, 1-b.healthf()), (b.block.size == 1 ? 0.3f : 0.25f) * 0.25f * b.block.size, false, Align.center);
                        Fonts.outline.draw((int)progress + "%",
                                b.x, b.y - b.block.size * 8 * 0.25f + 2,
                                Tmp.c1.set(Color.lightGray).lerp(Pal.accent, progress/100), (b.block.size == 1 ? 0.3f : 0.25f) * 0.25f * b.block.size, false, Align.center);
                    }
                    else Fonts.outline.draw((int)b.health + " / " + (int)b.maxHealth,
                                b.x, b.y - b.block.size * 8 * 0.25f,
                                Tmp.c1.set(Pal.items).lerp(Pal.health, 1-b.healthf()), (b.block.size == 1 ? 0.3f : 0.25f) * 0.25f * b.block.size, false, Align.center);
                });
            }

            if(settings.getBool("blockstatus")) Groups.build.each(build -> {
                if(Vars.player != null && player.team() == build.team) return;

                Block block = build.block;
                if(block.enableDrawStatus && block.consumes.any()){
                    float multiplier = block.size > 1 ? 1 : 0.64f;
                    float brcx = build.x + (block.size * tilesize / 2f) - (tilesize * multiplier / 2f);
                    float brcy = build.y - (block.size * tilesize / 2f) + (tilesize * multiplier / 2f);

                    Draw.color(Pal.gray);
                    Fill.square(brcx, brcy, 2.5f * multiplier, 45);
                    Draw.color(build.status().color);
                    Fill.square(brcx, brcy, 1.5f * multiplier, 45);
                    Draw.color();
                }
            });

            if(!mobile && !Vars.state.isPaused() && settings.getBool("gaycursor"))
                Fx.mine.at(Core.input.mouseWorldX(), Core.input.mouseWorldY(), Tmp.c2.set(Color.red).shiftHue(Time.time * 1.5f));

            if(!renderer.pixelator.enabled()) Groups.unit.each(unit -> unit.item() != null && unit.itemTime > 0.01f, unit ->
                Fonts.outline.draw(unit.stack.amount + "",
                        unit.x + Angles.trnsx(unit.rotation + 180f, unit.type.itemOffsetY),
                        unit.y + Angles.trnsy(unit.rotation + 180f, unit.type.itemOffsetY) - 3,
                        Pal.accent, 0.25f * unit.itemTime / Scl.scl(1f), false, Align.center)
            );

            if(!state.rules.polygonCoreProtection && player != null){
                state.teams.eachEnemyCore(player.team(), core -> {
                    if(Core.camera.bounds(Tmp.r1).overlaps(Tmp.r2.setCentered(core.x, core.y, state.rules.enemyCoreBuildRadius * 2f))){
                        Draw.color(Color.darkGray);
                        Lines.circle(core.x, core.y - 2, state.rules.enemyCoreBuildRadius);
                        Draw.color(Pal.accent, core.team.color, 0.5f + Mathf.absin(Time.time, 10f, 0.5f));
                        Lines.circle(core.x, core.y, state.rules.enemyCoreBuildRadius);
                    }
                });
            }

            if(settings.getBool("linkedMass")){
                if(target instanceof MassDriver.MassDriverBuild mass) {
                    linkedMasses.clear();
                    drawMassLink(mass);
                }
                else if(target instanceof PayloadMassDriver.PayloadDriverBuild mass) {
                    linkedPayloadMasses.clear();
                    drawMassPayloadLink(mass);
                }
            }

            if(settings.getBool("linkedNode") && target instanceof Building node){
                linkedNodes.clear();
                drawNodeLink(node);
            }

            if(settings.getBool("select") && target != null) {
                for(int i = 0; i < 4; i++){
                    float rot = i * 90f + 45f + (-Time.time) % 360f;
                    float length = (target instanceof Unit u ? u.hitSize : target instanceof Building b ? b.block.size * tilesize : 0) * 1.5f + 2.5f;
                    Draw.color(Tmp.c1.set(locked ? Color.orange : Color.darkGray).lerp(locked ? Color.scarlet : Color.gray, Mathf.absin(Time.time, 3f, 1f)).a(settings.getInt("selectopacity") / 100f));
                    Draw.rect("select-arrow", target.x() + Angles.trnsx(rot, length), target.y() + Angles.trnsy(rot, length), length / 1.9f, length / 1.9f, rot - 135f);
                }
                if(settings.getBool("distanceLine") && player.unit() != null && !player.unit().dead && target != null) { //need selected other unit with living player
                    Teamc from = player.unit();
                    Position to = target;
                    if(to == from) to = input.mouseWorld();
                    if(player.unit() instanceof BlockUnitUnit bu) Tmp.v1.set(bu.x() + bu.tile().block.offset, bu.y() + bu.tile().block.offset).sub(to.getX(), to.getY()).limit(bu.tile().block.size * tilesize + sin + 0.5f);
                    else Tmp.v1.set(from.x(), from.y()).sub(to.getX(), to.getY()).limit(player.unit().hitSize + sin + 0.5f);

                    float x2 = from.x() - Tmp.v1.x, y2 = from.y() - Tmp.v1.y,
                            x1 = to.getX() + Tmp.v1.x, y1 = to.getY() + Tmp.v1.y;
                    int segs = (int) (to.dst(from.x(), from.y()) / tilesize);
                    if(segs > 0){
                        Lines.stroke(2.5f, Pal.gray);
                        Lines.dashLine(x1, y1, x2, y2, segs);
                        Lines.stroke(1f, Pal.placing);
                        Lines.dashLine(x1, y1, x2, y2, segs);

                        Fonts.outline.draw(Strings.fixed(to.dst(from.x(), from.y()), 2) + " (" + segs + " " + bundle.get("tiles") + ")",
                                from.x() + Angles.trnsx(Angles.angle(from.x(), from.y(), to.getX(), to.getY()), player.unit().hitSize() + Math.min(segs, 6) * 8f),
                                from.y() + Angles.trnsy(Angles.angle(from.x(), from.y(), to.getX(), to.getY()), player.unit().hitSize() + Math.min(segs, 6) * 8f) - 3,
                                Pal.accent, 0.25f, false, Align.center);
                    }
                }
            }

            if(settings.getBool("rangeNearby") && player != null && player.unit() != null && !player.unit().dead) {
                Unit unit = player.unit();
                tmpbuildobj.clear();
                for(int i = 0; i < Team.baseTeams.length; i++){
                    if(!settings.getBool("aliceRange") && player.team() == Team.baseTeams[i]) continue;

                    int finalI = i;
                    tmpbuildobj.put(Team.baseTeams[i], Groups.build.copy(new Seq<Building>()).filter(b -> {
                        if(!(b instanceof BaseTurret.BaseTurretBuild)) return false;
                        boolean canHit = unit == null || (b.block instanceof Turret t ? unit.isFlying() ? t.targetAir : t.targetGround :
                                b.block instanceof TractorBeamTurret tu && (unit.isFlying() ? tu.targetAir : tu.targetGround));
                        float range = ((BaseTurret.BaseTurretBuild) b).range();
                        float max = range + settings.getInt("rangeRadius") * tilesize + b.block.offset;
                        float dst = Mathf.dst(control.input.getMouseX(), control.input.getMouseY(), b.x, b.y);
                        if(control.input.block != null && dst <= max) canHit = b.block instanceof Turret t && t.targetGround;

                        return ((finalI == 0 && (!canHit&&b.team!=Vars.player.team())) || (b.team == Team.baseTeams[finalI] && (b.team==Vars.player.team()||canHit))) &&
                        ((b instanceof Turret.TurretBuild t && t.hasAmmo()) || b.cons.valid()) &&
                        (camera.position.dst(b) <= max || (control.input.block != null && dst <= max)) &&
                        (canHit || settings.getBool("allTargetRange"));
                    }));
                }
                tmpbuildobj.each((t, bseq) -> {
                    if(settings.getBool("RangeShader")) Draw.drawRange(166+t.id*3, 1, () -> effectBuffer.begin(Color.clear), () -> {
                        effectBuffer.end();
                        effectBuffer.blit(turretRange);
                    });
                    Draw.color(t.color);
                    bseq.each(b -> {
                        float range = ((BaseTurret.BaseTurretBuild)b).range();
                        if(settings.getBool("RangeShader")) {
                            Draw.z(166+t.id*3);
                            Fill.poly(b.x, b.y, Lines.circleVertices(range), range);
                        }
                        else Drawf.dashCircle(b.x, b.y, range, t.color);
                    });
                });
            }
            Draw.reset();
        });
    }

    public static Tile getNextTile(Tile tile, int cost, Team team, int finder) {
        Pathfinder.Flowfield field = pathfinder.getField(team, cost, Mathf.clamp(finder, 0, 1));
        Tile tile1 = pathfinder.getTargetTile(tile, field);
        pathTiles.add(tile1);
        if(tile1 == tile || tile1 == null ||
                (finder == 0 && (otherCores != Groups.build.count(b -> b instanceof CoreBlock.CoreBuild && b.team != team) || tile1.build instanceof CoreBlock.CoreBuild)) ||
                (finder == 1 && tile1.build instanceof CommandCenter.CommandBuild))
            return tile1;
        return getNextTile(tile1, cost, team, finder);
    }

    public static void drawMassPayloadLink(PayloadMassDriver.PayloadDriverBuild from){
        float sin = Mathf.absin(Time.time, 6f, 1f);
        Groups.build.each(b -> b instanceof PayloadMassDriver.PayloadDriverBuild fromMass &&
                world.build(fromMass.link) == from &&
                from.within(fromMass.x, fromMass.y, ((PayloadMassDriver)fromMass.block).range) &&
                !linkedPayloadMasses.contains(from), b -> {
            linkedPayloadMasses.add((PayloadMassDriver.PayloadDriverBuild) b);
            drawMassPayloadLink((PayloadMassDriver.PayloadDriverBuild) b);
        });

        if(world.build(from.link) instanceof PayloadMassDriver.PayloadDriverBuild to && from != to &&
                to.within(from.x, from.y, ((PayloadMassDriver)from.block).range)){
            Tmp.v1.set(from.x + from.block.offset, from.y + from.block.offset).sub(to.x, to.y).limit(from.block.size * tilesize + sin + 0.5f);
            float x2 = from.x - Tmp.v1.x, y2 = from.y - Tmp.v1.y,
                    x1 = to.x + Tmp.v1.x, y1 = to.y + Tmp.v1.y;
            int segs = (int)(to.dst(from.x, from.y)/tilesize);

            Lines.stroke(4f, Pal.gray);
            Lines.dashLine(x1, y1, x2, y2, segs);
            Lines.stroke(2f, Pal.placing);
            Lines.dashLine(x1, y1, x2, y2, segs);
            Lines.stroke(1f, Pal.accent);
            Drawf.circles(from.x, from.y, (from.tile.block().size / 2f + 1) * tilesize + sin - 2f, Pal.accent);

            for(var shooter : from.waitingShooters){
                Drawf.circles(shooter.x, shooter.y, (from.tile.block().size / 2f + 1) * tilesize + sin - 2f);
                Drawf.arrow(shooter.x, shooter.y, from.x, from.y, from.block.size * tilesize + sin, 4f + sin);
            }
            if(from.link != -1 && world.build(from.link) instanceof PayloadMassDriver.PayloadDriverBuild other && other.block == from.block && other.team == from.team && from.within(other, ((PayloadMassDriver)from.block).range)){
                Building target = world.build(from.link);
                Drawf.circles(target.x, target.y, (target.block().size / 2f + 1) * tilesize + sin - 2f);
                Drawf.arrow(from.x, from.y, target.x, target.y, from.block.size * tilesize + sin, 4f + sin);
            }
            if(world.build(to.link) instanceof PayloadMassDriver.PayloadDriverBuild newTo && to != newTo &&
                    newTo.within(to.x, to.y, ((PayloadMassDriver)to.block).range) && !linkedPayloadMasses.contains(to)){
                linkedPayloadMasses.add(to);
                drawMassPayloadLink(to);
            }
        }
    }

    public static void drawMassLink(MassDriver.MassDriverBuild from){
        float sin = Mathf.absin(Time.time, 6f, 1f);
        Groups.build.each(b -> b instanceof MassDriver.MassDriverBuild fromMass &&
                world.build(fromMass.link) == from &&
                from.within(fromMass.x, fromMass.y, ((MassDriver)fromMass.block).range) &&
                !linkedMasses.contains(from), b -> {
            linkedMasses.add((MassDriver.MassDriverBuild) b);
            drawMassLink((MassDriver.MassDriverBuild) b);
        });

        if(world.build(from.link) instanceof MassDriver.MassDriverBuild to && from != to && to.within(from.x, from.y, ((MassDriver)from.block).range)){
            Tmp.v1.set(from.x + from.block.offset, from.y + from.block.offset).sub(to.x, to.y).limit(from.block.size * tilesize + sin + 0.5f);
            float x2 = from.x - Tmp.v1.x, y2 = from.y - Tmp.v1.y,
                    x1 = to.x + Tmp.v1.x, y1 = to.y + Tmp.v1.y;
            int segs = (int)(to.dst(from.x, from.y)/tilesize);

            Lines.stroke(4f, Pal.gray);
            Lines.dashLine(x1, y1, x2, y2, segs);
            Lines.stroke(2f, Pal.placing);
            Lines.dashLine(x1, y1, x2, y2, segs);
            Lines.stroke(1f, Pal.accent);
            Drawf.circles(from.x, from.y, (from.tile.block().size / 2f + 1) * tilesize + sin - 2f, Pal.accent);

            for(var shooter : from.waitingShooters){
                Drawf.circles(shooter.x, shooter.y, (from.tile.block().size / 2f + 1) * tilesize + sin - 2f);
                Drawf.arrow(shooter.x, shooter.y, from.x, from.y, from.block.size * tilesize + sin, 4f + sin);
            }
            if(from.link != -1 && world.build(from.link) instanceof MassDriver.MassDriverBuild other && other.block == from.block && other.team == from.team && from.within(other, ((MassDriver)from.block).range)){
                Building target = world.build(from.link);
                Drawf.circles(target.x, target.y, (target.block().size / 2f + 1) * tilesize + sin - 2f);
                Drawf.arrow(from.x, from.y, target.x, target.y, from.block.size * tilesize + sin, 4f + sin);
            }
            if(world.build(to.link) instanceof MassDriver.MassDriverBuild newTo && to != newTo &&
                    newTo.within(to.x, to.y, ((MassDriver)to.block).range) && !linkedMasses.contains(to)){
                linkedMasses.add(to);
                drawMassLink(to);
            }
        }
    }

    public static Seq<Building> getPowerLinkedBuilds(Building build) {
        linkedBuilds.clear();
        build.power.links.each(i -> linkedBuilds.add(world.build(i)));
        build.proximity().each(linkedBuilds::add);
        linkedBuilds.filter(b -> b != null && b.power != null);
        if(!build.block.outputsPower && !(build instanceof PowerNode.PowerNodeBuild))
            linkedBuilds.filter(b -> b.block.outputsPower || b instanceof PowerNode.PowerNodeBuild);
        return linkedBuilds;
    }

    public static void drawNodeLink(Building node) {
        if(node.power == null) return;
        if(!linkedNodes.contains(node)) {
            linkedNodes.add(node);
            getPowerLinkedBuilds(node).each(other -> {
                float angle1 = Angles.angle(node.x, node.y, other.x, other.y),
                        vx = Mathf.cosDeg(angle1), vy = Mathf.sinDeg(angle1),
                        len1 = node.block.size * tilesize / 2f - 1.5f, len2 = other.block.size * tilesize / 2f - 1.5f;

                Draw.color(Color.white, Color.valueOf("98ff98"), (1f - node.power.graph.getSatisfaction()) * 0.86f + Mathf.absin(3f, 0.1f));
                Draw.alpha(Renderer.laserOpacity);
                Drawf.laser(node.team, atlas.find("unitinfo-Slaser"), atlas.find("unitinfo-Slaser-end"), node.x + vx*len1, node.y + vy*len1, other.x - vx*len2, other.y - vy*len2, 0.25f);

                if(other.power != null) getPowerLinkedBuilds(other).each(OverDrawer::drawNodeLink);
            });
        }
    }
}
