import com.gurobi.gurobi.*;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.io.IOException;
import java.util.Locale;
import java.util.Random;

public class TemporalGurobiRunner {

    public static void main(String[] args) {
        System.out.println("=== Bateria Gurobi Temporal (SAND Completo) ===");
        System.out.println("Config: MIPGap=0.3 | CSV Output");
        System.out.printf("%-4s | %-5s | %-4s | %-17s | %-10s | %-15s\n", "N", "P", "Iter", "Status", "Tempo(s)", "Custo Otimo");
        System.out.println("--------------------------------------------------------------------------------");

        int[] numNodes = {10, 20, 50, 100, 200};
        int[] numPods = {50, 100, 200, 500, 1000, 5000, 10000};
        
        int T = 20; // 20 Time slots
        int numIterations = 10; 
        
        double delta = 50.0; // Boot latency penalty
        double theta = 20.0; // Shutdown logic penalty

        String csvFile = "resultados_gurobi_sand.csv";

        try (PrintWriter writer = new PrintWriter(new FileWriter(csvFile))) {
            writer.println("N,P,Iteration,Status,Time_s,Optimal_Cost");

            for (int N : numNodes) {
                for (int P : numPods) {
                    // Evitamos testar cenários inviáveis logicamente (menos pods que nós)
                    if (P < N) continue;

                    for (int it = 1; it <= numIterations; it++) {
                        Random rand = new Random(42 + N + P + it);

                        // ESTRUTURAS DE DADOS EXATAS DA SUA CLASSE 'Instance' DO ICUMT
                        double[] U = new double[N];             // Capacidade
                        double[] alpha = new double[N];         // Custo de Instalação/Abertura
                        double[] beta = new double[N];          // Custo de Alocação
                        double[] gamma = new double[N];         // Fator de Penalidade do Nó

                        // Lógica de sorteio EXATA do CustomMain.java
                        int capacityMin = P / N + 1; 
                        int capacityMax = P * 2; 

                        int openingCostInit = 1;
                        int openingCostEnd = 4 * N;

                        int allocatingCostInit = 1;
                        int allocatingCostEnd = 4 * N;

                        int errorPenalizationInit = 1;
                        int errorPenalizationEnd = 10;

                        for (int i = 0; i < N; i++) {
                            U[i] = capacityMin + rand.nextInt(capacityMax - capacityMin + 1); 
                            alpha[i] = openingCostInit + rand.nextInt(openingCostEnd - openingCostInit + 1); 
                            beta[i] = allocatingCostInit + rand.nextInt(allocatingCostEnd - allocatingCostInit + 1);
                            gamma[i] = errorPenalizationInit + rand.nextInt(errorPenalizationEnd - errorPenalizationInit + 1);
                        }

                        double[] u = new double[P]; // Demanda/Resource Usage do Pod
                        double[] e = new double[P]; // Errors do Pod
                        
                        int resourceUsageMin = 1;
                        int resourceUsageMax = 10;
                        int errorsInit = 1;
                        int errorsEnd = 20;

                        for (int j = 0; j < P; j++) {
                            u[j] = resourceUsageMin + rand.nextInt(resourceUsageMax - resourceUsageMin + 1); 
                            e[j] = errorsInit + rand.nextInt(errorsEnd - errorsInit + 1); 
                        }

                        runTemporalMIP(N, P, T, U, u, alpha, beta, gamma, e, delta, theta, it, writer);
                    }
                }
            }
            System.out.println("--------------------------------------------------------------------------------");
            System.out.println("Bateria finalizada! Resultados salvos no arquivo: " + csvFile);

        } catch (IOException e) {
            System.out.println("Erro ao criar o arquivo CSV: " + e.getMessage());
        }
    }

    public static void runTemporalMIP(int N, int P, int T, double[] U, double[] u, double[] alpha, double[] beta, 
                                      double[] gamma, double[] e, double delta, double theta, int iteracao, PrintWriter writer) {
        try {
            GRBEnv env = new GRBEnv(true);
            env.set("LogFile", "gurobi_" + N + "_" + P + "_it" + iteracao + ".log");
            env.set(GRB.IntParam.LogToConsole, 0); 
            env.set(GRB.DoubleParam.MIPGap, 0.01); // GAP Tolerado de 1%
            env.start();
            
            GRBModel model = new GRBModel(env);
            
            // 1. VARIÁVEIS TEMPORAIS DA SAND
            GRBVar[][] x = new GRBVar[N][T];
            GRBVar[][] z = new GRBVar[N][T];
            GRBVar[][] w = new GRBVar[N][T];
            GRBVar[][][] y = new GRBVar[N][P][T];

            for (int t = 0; t < T; t++) {
                for (int i = 0; i < N; i++) {
                    x[i][t] = model.addVar(0.0, 1.0, 0.0, GRB.BINARY, "x_" + i + "_" + t);
                    z[i][t] = model.addVar(0.0, 1.0, 0.0, GRB.BINARY, "z_" + i + "_" + t);
                    w[i][t] = model.addVar(0.0, 1.0, 0.0, GRB.BINARY, "w_" + i + "_" + t);
                    for (int j = 0; j < P; j++) {
                        y[i][j][t] = model.addVar(0.0, 1.0, 0.0, GRB.BINARY, "y_" + i + "_" + j + "_" + t);
                    }
                }
            }

            // 2. FUNÇÃO OBJETIVO: ICUMT (alpha, beta, gamma, e) + SAND (delta, theta)
            GRBLinExpr objective = new GRBLinExpr();
            for (int t = 0; t < T; t++) {
                for (int i = 0; i < N; i++) {
                    // Custo de manter o nó ligado (alpha)
                    objective.addTerm(alpha[i], x[i][t]); 
                    
                    // Multas temporais de inércia (SAND)
                    if (t < T - 1) {
                        objective.addTerm(delta, z[i][t]); 
                        objective.addTerm(theta, w[i][t]); 
                    }
                    
                    // Custos espaciais de alocação e penalidade de erro (ICUMT)
                    for (int j = 0; j < P; j++) {
                        objective.addTerm(beta[i], y[i][j][t]); 
                        objective.addTerm(gamma[i] * e[j], y[i][j][t]); 
                    }
                }
            }
            model.setObjective(objective, GRB.MINIMIZE);

            // 3. RESTRIÇÕES DA SAND
            for (int t = 0; t < T; t++) {
                
                // Viabilidade: Pelo menos um nó ativo
                GRBLinExpr minNodes = new GRBLinExpr();
                for (int i = 0; i < N; i++) {
                    minNodes.addTerm(1.0, x[i][t]);
                }
                model.addConstr(minNodes, GRB.GREATER_EQUAL, 1.0, "MinNodes_" + t);

                // Alocação Obrigatória
                for (int j = 0; j < P; j++) {
                    GRBLinExpr podAlloc = new GRBLinExpr();
                    for (int i = 0; i < N; i++) {
                        podAlloc.addTerm(1.0, y[i][j][t]);
                    }
                    model.addConstr(podAlloc, GRB.EQUAL, 1.0, "Alloc_" + j + "_" + t);
                }

                for (int i = 0; i < N; i++) {
                    // Capacidade Efetiva (NotReady State da SAND)
                    GRBLinExpr load = new GRBLinExpr();
                    for (int j = 0; j < P; j++) {
                        load.addTerm(u[j], y[i][j][t]);
                        
                        // Placement Validity
                        GRBLinExpr validPlace = new GRBLinExpr();
                        validPlace.addTerm(1.0, y[i][j][t]);
                        validPlace.addTerm(-1.0, x[i][t]);
                        model.addConstr(validPlace, GRB.LESS_EQUAL, 0.0, "ValidPlace_" + i + "_" + j + "_" + t);
                    }
                    
                    GRBLinExpr capacity = new GRBLinExpr();
                    capacity.addTerm(U[i], x[i][t]);
                    if (t < T - 1) {
                        capacity.addTerm(-U[i], z[i][t]);
                        capacity.addTerm(-U[i], w[i][t]);
                    }
                    model.addConstr(load, GRB.LESS_EQUAL, capacity, "Cap_" + i + "_" + t);

                    // Transições Temporais (A essência da SAND)
                    if (t < T - 1) {
                        GRBLinExpr contExpr = new GRBLinExpr();
                        contExpr.addTerm(1.0, x[i][t+1]);
                        contExpr.addTerm(-1.0, x[i][t]);
                        contExpr.addTerm(-1.0, z[i][t]);
                        contExpr.addTerm(1.0, w[i][t]);
                        model.addConstr(contExpr, GRB.EQUAL, 0.0, "Cont_" + i + "_" + t);

                        GRBLinExpr actExpr = new GRBLinExpr();
                        actExpr.addTerm(1.0, z[i][t]);
                        actExpr.addTerm(-1.0, x[i][t+1]);
                        actExpr.addTerm(1.0, x[i][t]);
                        model.addConstr(actExpr, GRB.GREATER_EQUAL, 0.0, "Act_" + i + "_" + t);

                        GRBLinExpr deactExpr = new GRBLinExpr();
                        deactExpr.addTerm(1.0, w[i][t]);
                        deactExpr.addTerm(-1.0, x[i][t]);
                        deactExpr.addTerm(1.0, x[i][t+1]);
                        model.addConstr(deactExpr, GRB.GREATER_EQUAL, 0.0, "Deact_" + i + "_" + t);
                    }
                }
            }

            model.optimize();

            int status = model.get(GRB.IntAttr.Status);
            String statusStr = status == GRB.Status.OPTIMAL ? "OPTIMAL_1_GAP" : (status == GRB.Status.INFEASIBLE ? "INFEASIBLE" : "OTHER");
            double objVal = -1.0;
            double runtime = model.get(GRB.DoubleAttr.Runtime);

            try { objVal = model.get(GRB.DoubleAttr.ObjVal); } catch (Exception ex) { }

            System.out.printf(Locale.US, "%-4d | %-5d | %-4d | %-17s | %-10.3f | %-15.2f\n", N, P, iteracao, statusStr, runtime, objVal);
            
            if (writer != null) {
                writer.printf(Locale.US, "%d,%d,%d,%s,%.3f,%.2f\n", N, P, iteracao, statusStr, runtime, objVal);
                writer.flush();
            }

            model.dispose();
            env.dispose();

            System.gc(); // Força o Garbage Collector a limpar a RAM agora!

        } catch (GRBException ex) {
            System.out.printf(Locale.US, "%-4d | %-5d | %-4d | %-17s | %-10.3f | %-15.2f\n", N, P, iteracao, "ERROR", 0.0, 0.0);
            if (writer != null) {
                writer.printf(Locale.US, "%d,%d,%d,%s,%.3f,%.2f\n", N, P, iteracao, "ERROR", 0.0, 0.0);
                writer.flush();
            }
        }
    }
}