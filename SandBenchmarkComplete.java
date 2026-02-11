import java.io.FileWriter;
import java.io.IOException;
import java.util.*;

class SandPod {
    int id, size;
    public SandPod(int id, int size) { this.id = id; this.size = size; }
}

class SandNode implements Cloneable {
    int id, capacity;
    double alpha, delta, theta; 
    Map<Integer, SandPod> pods = new HashMap<>();

    public SandNode(int id, int cap, double a, double d, double t) {
        this.id = id; this.capacity = cap;
        this.alpha = a; this.delta = d; this.theta = t;
    }

    public int getUsed() {
        return pods.values().stream().mapToInt(p -> p.size).sum();
    }

    public boolean canFit(SandPod p) {
        return getUsed() + p.size <= capacity;
    }

    public boolean isActive() { return !pods.isEmpty(); }

    @Override
    public SandNode clone() {
        SandNode copy = new SandNode(id, capacity, alpha, delta, theta);
        copy.pods = new HashMap<>(this.pods);
        return copy;
    }
}

public class SandBenchmarkComplete {

    public static void main(String[] args) throws IOException {
        String filename = "benchmark_final_sand_2026.csv";
        FileWriter writer = new FileWriter(filename);
        writer.write("Ratio_R;TamanhoPod;TamanhoNode;Execucao;Slot;Pods;Cost_Baseline;Cost_Temporal\n");

        double[] ratios = {1.0, 10.0, 100.0};
        int[] podSizes = {50, 100, 200, 500, 1000, 5000, 10000};
        int[] nodeSizes = {10, 20, 50, 100, 200};
        
        int numExecutions = 5; 
        int timeSlots = 20;
        Random rand = new Random(42);

        System.out.println("Iniciando Benchmark Completo...");

        for (double R : ratios) {
            for (int pSize : podSizes) {
                for (int nSize : nodeSizes) {
                    System.out.printf("R=%.1f | Pods=%d | Nodes=%d\n", R, pSize, nSize);
                    
                    for (int e = 1; e <= numExecutions; e++) {
                        
                        List<SandNode> baseNodes = new ArrayList<>();
                        for (int i = 0; i < nSize; i++) {
                            double alpha = (i < nSize / 2) ? 20.0 : 250.0; 
                            double delta = (i < nSize / 2) ? (alpha * R * 5) : 10.0;
                            int cap = Math.max(100, (pSize * 10) / nSize); 
                            baseNodes.add(new SandNode(i, cap, alpha, delta, delta * 0.1));
                        }

                        List<SandNode> nodesB = new ArrayList<>();
                        List<SandNode> nodesT = new ArrayList<>();
                        for(SandNode n : baseNodes) {
                            nodesB.add(n.clone());
                            nodesT.add(n.clone());
                        }

                        Set<Integer> memoryB = new HashSet<>();
                        Set<Integer> memoryT = new HashSet<>();

                        for (int s = 1; s <= timeSlots; s++) {
                            double variacao = 0.7 + (rand.nextDouble() * 0.6);
                            int target = (int)(pSize * variacao);
                            List<SandPod> currentPods = new ArrayList<>();
                            for(int i=0; i < target; i++) currentPods.add(new SandPod(i, 5));

                            for(SandNode n : nodesB) n.pods.clear();
                            nodesB.sort(Comparator.comparingDouble(n -> n.alpha));
                            for(SandPod p : currentPods) {
                                for(SandNode n : nodesB) if(n.canFit(p)) { n.pods.put(p.id, p); break; }
                            }
                            double costB = calculate(nodesB, memoryB);
                            updateMemory(nodesB, memoryB);

                            for(SandNode n : nodesT) n.pods.clear();
                            final Set<Integer> prevActive = new HashSet<>(memoryT);
                            nodesT.sort((n1, n2) -> {
                                double c1 = n1.alpha + (prevActive.contains(n1.id) ? 0 : n1.delta);
                                double c2 = n2.alpha + (prevActive.contains(n2.id) ? 0 : n2.delta);
                                return Double.compare(c1, c2);
                            });
                            for(SandPod p : currentPods) {
                                for(SandNode n : nodesT) if(n.canFit(p)) { n.pods.put(p.id, p); break; }
                            }
                            double costT = calculate(nodesT, memoryT);
                            updateMemory(nodesT, memoryT);

                            writer.write(R + ";" + pSize + ";" + nSize + ";" + e + ";" + s + ";" + target + ";" + costB + ";" + costT + "\n");
                        }
                    }
                }
            }
        }
        writer.close();
        System.out.println("Benchmark finalizado com sucesso! Arquivo: " + filename);
    }

    private static double calculate(List<SandNode> nodes, Set<Integer> prev) {
        double total = 0;
        for (SandNode n : nodes) {
            boolean active = n.isActive();
            boolean wasActive = prev.contains(n.id);
            if (active) {
                total += n.alpha;
                if (!wasActive) total += n.delta;
                total += n.pods.size() * 2.5;
            } else if (wasActive) {
                total += n.theta;
            }
        }
        return total;
    }

    private static void updateMemory(List<SandNode> nodes, Set<Integer> state) {
        state.clear();
        for(SandNode n : nodes) if(n.isActive()) state.add(n.id);
    }
}