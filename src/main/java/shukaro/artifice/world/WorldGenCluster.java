package shukaro.artifice.world;

import cofh.api.world.IFeatureGenerator;
import gnu.trove.map.TMap;
import gnu.trove.map.hash.THashMap;
import gnu.trove.set.hash.THashSet;
import net.minecraft.block.Block;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;
import net.minecraft.world.biome.BiomeGenBase;
import net.minecraftforge.common.BiomeDictionary;
import shukaro.artifice.ArtificeBlocks;
import shukaro.artifice.ArtificeCore;
import shukaro.artifice.ArtificeRegistry;
import shukaro.artifice.block.decorative.BlockOre;
import shukaro.artifice.event.ArtificeTickHandler;
import shukaro.artifice.util.BlockCoord;
import shukaro.artifice.util.ChunkCoord;
import shukaro.artifice.util.NameMetaPair;

import javax.lang.model.element.Name;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Random;
import java.util.Set;

public class WorldGenCluster implements IFeatureGenerator
{
    protected Block block;
    protected int meta;
    private int minHeight;
    private int maxHeight;
    private int size;
    private int frequency;
    protected Set<NameMetaPair> replaced;

    public WorldGenCluster(Block block, int meta, int minHeight, int maxHeight, int size, int frequency)
    {
        this.block = block;
        this.meta = meta;
        this.minHeight = minHeight;
        this.maxHeight = maxHeight;
        this.size = size;
        this.frequency = frequency;
        this.replaced = ArtificeRegistry.getStoneTypes();
    }

    @Override
    public String getFeatureName()
    {
        return ArtificeCore.modName + ": " + new ItemStack(block, 1, meta).getDisplayName() + " Cluster";
    }

    @Override
    public boolean generateFeature(Random rand, int chunkX, int chunkZ, World world, boolean newGen)
    {
        int total;
        if (frequency >= 100)
            total = (rand.nextInt(100) < Math.abs(frequency % (int) Math.pow(10, (int) Math.log10(frequency)))) ? (frequency / 100) + 1 : (frequency / 100);
        else
            total = rand.nextInt(100) < frequency ? 0 : 1;
        for (int j=0; j<total; j++)
        {
            boolean doGen = false;
            BlockCoord b = new BlockCoord();
            int dim = world.provider.dimensionId;

            int tries = rand.nextInt(16) + 4;
            for (int i = 0; i < tries; i++)
            {
                int x = (chunkX << 4) + rand.nextInt(16);
                int y = rand.nextInt((maxHeight - minHeight) + 1) + minHeight;
                int z = (chunkZ << 4) + rand.nextInt(16);
                b.set(x, y, z);
                if (canGenHere(world, b))
                {
                    doGen = true;
                    break;
                }
            }
            if (!doGen)
                return false;

            if (block.getUnlocalizedName().contains("limestone"))
            {
                BiomeGenBase biome = world.getBiomeGenForCoords(b.x, b.z);
                if (BiomeDictionary.isBiomeOfType(biome, BiomeDictionary.Type.SANDY))
                {
                    if (b.y > world.getHeightValue(b.x, b.z) / 2)
                        block = ArtificeBlocks.blockLimestones[3];
                    else
                        block = ArtificeBlocks.blockLimestones[2];
                } else if (BiomeDictionary.isBiomeOfType(biome, BiomeDictionary.Type.WATER))
                    block = ArtificeBlocks.blockLimestones[5];
                else if (BiomeDictionary.isBiomeOfType(biome, BiomeDictionary.Type.JUNGLE) || BiomeDictionary.isBiomeOfType(biome, BiomeDictionary.Type.SWAMP))
                    block = ArtificeBlocks.blockLimestones[6];
                else if (BiomeDictionary.isBiomeOfType(biome, BiomeDictionary.Type.MESA) || BiomeDictionary.isBiomeOfType(biome, BiomeDictionary.Type.MOUNTAIN))
                    block = ArtificeBlocks.blockLimestones[4];
                else
                {
                    if (b.y > world.getHeightValue(b.x, b.z) / 2)
                        block = ArtificeBlocks.blockLimestones[1];
                    else
                        block = ArtificeBlocks.blockLimestones[0];
                }
            }

            int num = rand.nextInt((int) (size * 1.5) - (int) (size * 0.5) + 1) + (int) (size * 0.5);
            ClusterGen cluster = new ClusterGen(world, rand, b, num);
            cluster.doGeneration(chunkX, chunkZ);
            if (!ArtificeTickHandler.toGen.containsKey(Integer.valueOf(dim)))
                ArtificeTickHandler.toGen.put(dim, new ArrayDeque<ISuspendableGen>());
            if (cluster.getChunksToGen().size() > 0)
                ArtificeTickHandler.toGen.get(Integer.valueOf(dim)).add(cluster);
        }
        return true;
    }

    protected boolean canGenHere(World world, BlockCoord b)
    {
        NameMetaPair pair = new NameMetaPair(b.getBlock(world), b.getMeta(world));
        return !b.isAir(world) && (replaced.contains(pair) || ArtificeBlocks.oreSet.contains(pair));
    }

    private class ClusterGen implements ISuspendableGen
    {
        private World world;
        private Random rand;
        private BlockCoord coord;
        private TMap<ChunkCoord, ArrayList<BlockCoord>> toGen;
        private int size;

        public ClusterGen(World world, Random rand, BlockCoord start, int size)
        {
            this.world = world;
            this.rand = rand;
            this.coord = start;
            this.toGen = new THashMap<ChunkCoord, ArrayList<BlockCoord>>();
            this.size = size;
        }

        @Override
        public Set<ChunkCoord> getChunksToGen()
        {
            return toGen.keySet();
        }

        @Override
        public boolean doGeneration(int chunkX, int chunkZ)
        {
            ChunkCoord chunk = new ChunkCoord(chunkX, chunkZ);
            int genned = 0;
            int threshold = size / 8;
            while (genned < size)
            {
                for (BlockCoord t : coord.getAdjacent())
                {
                    ChunkCoord c = new ChunkCoord(t);
                    if (!toGen.containsKey(c))
                        toGen.put(c, new ArrayList<BlockCoord>());
                    if (!chunk.contains(t) && !world.blockExists(t.x, t.y, t.z))
                        return true;
                    else if (canGenHere(world, t) && !toGen.get(chunk).contains(t))
                    {
                        if (toGen.get(chunk) == null)
                            toGen.put(chunk, new ArrayList<BlockCoord>());
                        toGen.get(chunk).add(t);
                    }
                }
                if (toGen.get(chunk).size() == 0)
                    break;
                coord.set(toGen.get(chunk).get(rand.nextInt(toGen.get(chunk).size())));
                toGen.get(chunk).remove(coord);

                NameMetaPair ore = new NameMetaPair(coord.getBlock(world), coord.getMeta(world));
                if (replaced.contains(ore))
                    world.setBlock(coord.x, coord.y, coord.z, block, meta, 0);
                else if (ArtificeBlocks.oreSet.contains(ore))
                {
                    NameMetaPair newOre = BlockOre.getOre(ore, block);
                    if (newOre != null)
                        world.setBlock(coord.x, coord.y, coord.z, newOre.getBlock(), newOre.getMetadata(), 0);
                }

                genned++;
                while (toGen.get(chunk).size() > threshold && genned < size)
                {
                    if (toGen.get(chunk).size() == 0)
                        break;
                    coord.set(toGen.get(chunk).get(rand.nextInt(toGen.get(chunk).size())));
                    toGen.get(chunk).remove(coord);

                    ore = new NameMetaPair(coord.getBlock(world), coord.getMeta(world));
                    if (replaced.contains(ore))
                        world.setBlock(coord.x, coord.y, coord.z, block, meta, 0);
                    else if (ArtificeBlocks.oreSet.contains(ore))
                    {
                        NameMetaPair newOre = BlockOre.getOre(ore, block);
                        if (newOre != null)
                            world.setBlock(coord.x, coord.y, coord.z, newOre.getBlock(), newOre.getMetadata(), 0);
                    }

                    genned++;
                }
            }
            return true;
        }
    }
}
