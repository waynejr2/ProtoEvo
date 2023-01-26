package com.protoevo.biology.evolution;

import com.protoevo.biology.neat.NetworkGenome;
import com.protoevo.biology.neat.NeuralNetwork;
import com.protoevo.core.Simulation;
import com.protoevo.core.settings.SimulationSettings;

import java.io.Serializable;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.*;
import java.util.function.Function;

public class GeneExpressionFunction implements Evolvable.Component, Serializable {

    public static class ExpressionNodes extends HashMap<String, ExpressionNode> {}
    public static class Regulators extends HashMap<String, RegulationNode> {}

    public static class RegulationNode implements Serializable {
        public static final long serialVersionUID = 1L;
        public String name;
        private final Function<Evolvable, Float> regulatorGetter;
        private String targetID;
        private Object lastTarget;

        public RegulationNode(String name, Function<Evolvable, Float> regulatorGetter) {
            this.name = name;
            this.regulatorGetter = regulatorGetter;
        }

        public RegulationNode(String name, Function<Evolvable, Float> regulatorGetter, String targetID) {
            this.name = name;
            this.regulatorGetter = regulatorGetter;
            this.targetID = targetID;
        }

        public void setTargetID(Evolvable target) {
            this.targetID = target.name();
        }

        public float getValue(Evolvable evolvable) {
            lastTarget = evolvable;
            return regulatorGetter.apply(evolvable);
        }

        public Object getLastTarget() {
            return lastTarget;
        }

        public Function<Evolvable, Float> getGetter() {
            return regulatorGetter;
        }

        public RegulationNode copy() {
            return new RegulationNode(name, regulatorGetter, targetID);
        }

        public String getName() {
            return name;
        }
    }

    public static class ExpressionNode implements Serializable {
        public static final long serialVersionUID = 1L;
        private String name;
        private final Trait<?> trait;
        private final Method traitSetter;
        private final Map<String, Object> dependencies;
        private String[] dependents;
        private Object lastTraitValue;
        private String targetID;
        private Object lastTarget;

        public ExpressionNode(String name, Trait<?> trait, Method traitSetter, String[] dependencies) {
            this.name = name;
            this.trait = trait;
            this.traitSetter = traitSetter;
            this.dependencies = new HashMap<>();
            for (String str : dependencies)
                if (!str.equals(""))
                    addDependency(str);
            dependents = new String[]{};
        }

        public ExpressionNode(String name, Trait<?> trait, Method traitSetter, String targetID,
                              Map<String, Object> dependencies, String[] dependents) {
            this.name = name;
            this.trait = trait;
            this.traitSetter = traitSetter;
            this.targetID = targetID;
            this.dependencies = dependencies;
            this.dependents = dependents;
        }

        public ExpressionNode(String name, Trait<?> trait, Method traitSetter) {
            this(name, trait, traitSetter, new String[]{});
        }

        public void addDependency(String geneName) {
            dependencies.put(geneName, null);
        }

        public void addDependent(String dependentName) {
            dependents = Arrays.copyOf(dependents, dependents.length + 1);
            dependents[dependents.length - 1] = dependentName;
        }

        public String[] getDependents() {
            return dependents;
        }

        public ExpressionNode copy(float mutationChance) {
            Trait<?> newTrait = Simulation.RANDOM.nextFloat() < mutationChance ? trait.mutate() : trait.copy();
            return new ExpressionNode(name, newTrait, traitSetter, targetID, dependencies, dependents);
        }

        public Trait<?> getTrait() {
            return trait;
        }

        public void setTargetEvolvable(String id) {
            this.targetID = id;
        }

        public String getTargetID() {
            return targetID;
        }

        public boolean acceptsEvolvable(Class<? extends Evolvable> evolvableType) {
            return mapsToTrait() && traitSetter.getDeclaringClass().equals(evolvableType);
        }

        public void setTraitValue(Evolvable target, Object traitValue) {
            if (!traitValue.equals(lastTraitValue)) {
                Evolvable.setTraitValue(target, traitSetter, traitValue);
                lastTraitValue = traitValue;
                lastTarget = target;
            }
        }

        public Object getLastTarget() {
            return lastTarget;
        }

        public String getName() {
            return name;
        }

        public Map<String, Object> getDependencies() {
            return dependencies;
        }

        public boolean mapsToTrait() {
            return traitSetter != null;
        }

        public Method getTraitSetter() {
            return traitSetter;
        }

        public void prependName(String name) {
            this.name = name + "/" + this.name;
        }
    }

    public static final long serialVersionUID = 1L;
    private ExpressionNodes expressionNodes;
    private float mutationChance = SimulationSettings.globalMutationChance;
    private NetworkGenome grnGenome;
    private NeuralNetwork geneRegulatoryNetwork;
    private Regulators regulators;
    private Collection<String> regulatedTraits = new ArrayList<>();
    private final Map<String, Evolvable> targetMap = new HashMap<>();

    public GeneExpressionFunction(ExpressionNodes expressionNodes, Regulators regulators) {
        this.expressionNodes = expressionNodes;
        this.regulators = regulators;
    }

    public GeneExpressionFunction(Regulators regulators) {
        this(new ExpressionNodes(), regulators);
    }

    @EvolvableFloat(name="Mutation Chance",
            min=SimulationSettings.minMutationChance, max=SimulationSettings.maxMutationChance)
    public void setMutationChance(float mutationChance) {
        this.mutationChance = mutationChance;
    }

    public void buildGeneRegulatoryNetwork() {
        grnGenome = GeneRegulatoryNetworkFactory.createNetworkGenome(this);
        geneRegulatoryNetwork = grnGenome.phenotype();

        for (int i = 0; i < geneRegulatoryNetwork.getDepth() + 1; i++)
            tick();
    }

    @Override
    public void build() {
        Component.super.build();
        buildGeneRegulatoryNetwork();
    }

    public NeuralNetwork getRegulatoryNetwork() {
        return geneRegulatoryNetwork;
    }

    private void setGRNInputs() {
        geneRegulatoryNetwork.setInput("Bias", 1f);
        for (String geneName : getTraitNames()) {
            if (geneRegulatoryNetwork.hasSensor(GeneRegulatoryNetworkFactory.getInputName(geneName))) {
                if (notDisabled(geneName)) {
                    Object geneValue = getGeneValue(geneName);
                    float value;
                    if (geneValue instanceof Float)
                        value = (float) geneValue;
                    else if (geneValue instanceof Integer)
                        value = (int) geneValue;
                    else if (geneValue instanceof Boolean)
                        value = ((boolean) geneValue) ? 1f : -1f;
                    else
                        throw new RuntimeException("Could not cast gene " + geneName + " value to float.");

                    geneRegulatoryNetwork.setInput(GeneRegulatoryNetworkFactory.getInputName(geneName), value);
                } else {
                    geneRegulatoryNetwork.setInput(GeneRegulatoryNetworkFactory.getInputName(geneName), 0);
                }
            }
        }

        for (String regulatorName : regulators.keySet()) {
            RegulationNode node = regulators.get(regulatorName);
            if (!targetMap.containsKey(node.targetID))
                continue;

            Evolvable target = targetMap.get(node.targetID);
            if (geneRegulatoryNetwork.hasSensor(regulatorName))
                geneRegulatoryNetwork.setInput(regulatorName, node.getValue(target));
        }
    }

    public void setGeneRegulators(Regulators regulators) {
        this.regulators = regulators;
    }

    public void registerTargetEvolvable(String id, Evolvable evolvable) {
        targetMap.put(id, evolvable);

        for (String trait : getTraitNames()) {
            ExpressionNode node = expressionNodes.get(trait);

            // only register to node if it is not already registered to another evolvable
            if (node.getTargetID() == null && node.acceptsEvolvable(evolvable.getClass())) {
                node.setTargetEvolvable(id);
                node.setTraitValue(evolvable, getTraitValue(trait));
            }
        }

        for (String regulatorName : regulators.keySet()) {
            RegulationNode node = regulators.get(regulatorName);
            if (node.targetID == null)
                node.targetID = id;
        }
    }

    public void tick() {
        if (geneRegulatoryNetwork == null)
            return;
        setGRNInputs();
        geneRegulatoryNetwork.tick();
        setGRNInputs();
    }

    public void update() {
        tick();
        for (String trait : getTraitNames()) {
            ExpressionNode node = expressionNodes.get(trait);
            if (node.mapsToTrait() && targetMap.containsKey(node.getTargetID()))
                node.setTraitValue(targetMap.get(node.getTargetID()), getTraitValue(trait));
        }
    }

    public void addRegulatedFloat(RegulatedFloat regulatedFloat, Method method) {
        String name = regulatedFloat.name();
        float minValue = regulatedFloat.min();
        float maxValue = regulatedFloat.max();
        RegulatedFloatTrait trait = new RegulatedFloatTrait(name, minValue, maxValue);
        trait.setGeneExpressionFunction(this);
        ExpressionNode node = new ExpressionNode(name, trait, method);
        addNode(name, node);
    }

    public void addEvolvableFloat(EvolvableFloat evolvableFloat, Method method) {
        String name = evolvableFloat.name();
        float minValue = evolvableFloat.min();
        float maxValue = evolvableFloat.max();
        FloatTrait trait;
        if (evolvableFloat.randomInitialValue())
            trait = new FloatTrait(name, minValue, maxValue);
        else
            trait = new FloatTrait(name, minValue, maxValue, evolvableFloat.initValue());

        trait.setRegulated(evolvableFloat.regulated());
        trait.setGeneExpressionFunction(this);
        ExpressionNode node = new ExpressionNode(name, trait, method, evolvableFloat.geneDependencies());
        addNode(name, node);
    }

    public void addEvolvableInteger(EvolvableInteger evolvableInteger, Method method) {
        String name = evolvableInteger.name();
        int minValue = evolvableInteger.min();
        int maxValue = evolvableInteger.max();
        EvolvableInteger.MutationMethod mutMethod = evolvableInteger.mutateMethod();
        int maxInc = evolvableInteger.maxIncrement();
        boolean canDisable = evolvableInteger.canDisable();
        int disableValue = evolvableInteger.disableValue();

        IntegerTrait trait;
        if (evolvableInteger.randomInitialValue())
            trait = new IntegerTrait(
                    name, minValue, maxValue, mutMethod, maxInc, canDisable, disableValue, false);
        else
            trait = new IntegerTrait(
                    name, minValue, maxValue, mutMethod, maxInc, canDisable, disableValue, false,
                    evolvableInteger.initValue());

        trait.setGeneExpressionFunction(this);
        addNode(name, new ExpressionNode(name, trait, method, evolvableInteger.geneDependencies()));
    }

    private Trait<?> constructTrait(String className, String traitName) {
        Class<Trait<?>> traitClass;
        try {
            traitClass = (Class<Trait<?>>) Class.forName(className);
        } catch (ClassNotFoundException e) {
            throw new RuntimeException("Failed to find trait class: " + e);
        } catch (ClassCastException e) {
            throw new RuntimeException("Class is not a trait: " + e);
        }

        Constructor<Trait<?>> traitConstructor;
        try {
            traitConstructor = traitClass.getConstructor(String.class);
        } catch (NoSuchMethodException e) {
            throw new RuntimeException(
                    "EvolvableObject gene class " + className + " did not have " +
                    "a constructor that takes a single string (the name): " + e);
        }

        try {
            return traitConstructor.newInstance(traitName);
        } catch (InstantiationException | IllegalAccessException | InvocationTargetException e) {
            throw new RuntimeException("Failed to construct trait from " + className + ": " + e);
        }
    }

    public void addEvolvableObject(EvolvableObject evolvable, Method method) {
        String name = method.getDeclaringClass().getSimpleName() + "/" + evolvable.name();
        String geneClassName = evolvable.traitClass();
        Trait<?> trait = constructTrait(geneClassName, name);
        trait.setGeneExpressionFunction(this);
        addNode(name, new ExpressionNode(name, trait, method, evolvable.dependencies()));
    }

    public void addEvolvableCollection(GeneExpressionFunction geneExpressionFunction,
                                       EvolvableCollection evolvable,
                                       Method method) {
        String name = evolvable.name();
        int minSize = evolvable.minSize();
        int maxSize = evolvable.maxSize();
        int initialSize = evolvable.initialSize();

        Class<Evolvable.Element> elementClass;
        try {
            elementClass = (Class<Evolvable.Element>) Class.forName(evolvable.elementClassPath());
        } catch (ClassNotFoundException e) {
            throw new RuntimeException("Failed to find class for evolvable collection: " + e);
        } catch (ClassCastException e) {
            throw new RuntimeException("EvolvableCollection element class path " + evolvable.elementClassPath() +
                    " did not refer to a class that implements Evolvable: " + e);
        }

        CollectionTrait trait = new CollectionTrait(
                geneExpressionFunction, elementClass, name, minSize, maxSize, initialSize);
        trait.setGeneExpressionFunction(this);
        addNode(name, new ExpressionNode(name, trait, method, evolvable.geneDependencies()));
    }

    public Method getTraitSetter(String geneName) {
        return expressionNodes.get(geneName).getTraitSetter();
    }

    public Trait<?> getTraitGene(String geneName) {
        return expressionNodes.get(geneName).getTrait();
    }

    public Collection<String> getTraitNames() {
        return expressionNodes.keySet();
    }

    public void merge(GeneExpressionFunction other) {
        other.expressionNodes.forEach(expressionNodes::putIfAbsent);
        other.regulators.forEach(regulators::putIfAbsent);
        other.targetMap.forEach(targetMap::putIfAbsent);
    }

    public Regulators getGeneRegulators() {
        return regulators;
    }

    public Collection<String> getRegulatedTraits() {
        return regulatedTraits;
    }

    public Object getGeneValue(String name) {
        Map<String, Object> deps = expressionNodes.get(name).getDependencies();
        deps.replaceAll((d, v) -> getTraitValue(d));
        return getTraitGene(name).getValue(deps);
    }

    public Object getTraitValue(String name) {
        if (hasGene(name)) {
            if (notDisabled(name) && geneRegulatoryNetwork != null
                    && geneRegulatoryNetwork.hasOutput(GeneRegulatoryNetworkFactory.getOutputName(name))) {
                float grnOutput = geneRegulatoryNetwork.getOutput(GeneRegulatoryNetworkFactory.getOutputName(name));
                Trait<?> trait = getTraitGene(name);
                return parseGRNOutput(trait, grnOutput);
            } else
                return getGeneValue(name);
        }
        throw new RuntimeException("Asked to get value for gene " + name + " that does not exist: " + this);
    }

    private boolean notDisabled(String name) {
        return !getTraitGene(name).isDisabled();
    }

    private Object parseGRNOutput(Trait<?> trait, float grnOutput) {
        if (trait instanceof FloatTrait)
            return grnOutput;
        if (trait instanceof IntegerTrait)
            return IntegerTrait.fromFloat(grnOutput);
        if (trait instanceof BooleanTrait)
            return BooleanTrait.fromFloat(grnOutput);
        throw new RuntimeException("Could not parse GRN output: gene=" + trait + ", output=" + grnOutput);
    }

    public GeneExpressionFunction cloneWithMutation() {
        ExpressionNodes newNodes = new ExpressionNodes();
        Regulators newRegulators = new Regulators();
        GeneExpressionFunction newFn = new GeneExpressionFunction(newNodes, newRegulators);

        for (Map.Entry<String, ExpressionNode> entry : expressionNodes.entrySet()) {
            ExpressionNode newNode = entry.getValue().copy(mutationChance);
            newNode.trait.setGeneExpressionFunction(newFn);
            newNodes.put(entry.getKey(), newNode);
        }

        regulators.forEach(
                (regulator, regulationNode) -> newRegulators.put(regulator, regulationNode.copy()));

        if (grnGenome != null) {
            newFn.grnGenome = new NetworkGenome(grnGenome);
            if (Math.random() < mutationChance)
                newFn.grnGenome.mutate();
        }

        return newFn;
    }

    public float getMutationRate() {
        return mutationChance;
    }

    public boolean hasGene(String geneName) {
        return expressionNodes.containsKey(geneName);
    }

    public ExpressionNode getNode(String geneName) {
        return expressionNodes.get(geneName);
    }

    public void addNode(String geneName, ExpressionNode node) {
        expressionNodes.put(geneName, node);
        if (node.getTrait().canDisable()) {
            String disableName = "Disable " + geneName;
            node.addDependency(disableName);
            BooleanTrait disableGene = new BooleanTrait(disableName, false);
            ExpressionNode disableNode = new ExpressionNode(disableName, disableGene, null);
            disableNode.addDependent(geneName);
            expressionNodes.put(disableName, disableNode);
        }
    }

    public void prependNames(String name) {
        ExpressionNodes newNodes = new ExpressionNodes();
        for (Map.Entry<String, ExpressionNode> entry : expressionNodes.entrySet()) {
            ExpressionNode node = entry.getValue();
            String newName = name + "/" + entry.getKey();
            newNodes.put(newName, node);

            node.prependName(name);
            for (String dependent : node.getDependents()) {
                Map<String, Object> dependentsDependencies = getNode(dependent).getDependencies();
                dependentsDependencies.put(
                        name + "/" + node.name, dependentsDependencies.get(node.name)
                );
            }
        }
        expressionNodes = newNodes;

        Regulators newRegulators = new Regulators();
        for (String regulator : regulators.keySet()) {
            RegulationNode node = regulators.get(regulator);
            node.name = name + "/" + node.name;
            newRegulators.put(name + "/" + regulator, node);
        }
        regulators = newRegulators;

        List<String> newRegulatedTraits = new ArrayList<>();
        for (String regulatedTrait : regulatedTraits) {
            newRegulatedTraits.add(name + "/" + regulatedTrait);
        }
        regulatedTraits = newRegulatedTraits;
    }

    public ExpressionNodes getGenes() {
        return expressionNodes;
    }

    @Override
    public String toString() {
        StringBuilder s = new StringBuilder();
        for (String geneName : expressionNodes.keySet()) {
            s.append(geneName).append(": ").append(expressionNodes.get(geneName)).append("\n");
        }
        return s.toString();
    }
}
