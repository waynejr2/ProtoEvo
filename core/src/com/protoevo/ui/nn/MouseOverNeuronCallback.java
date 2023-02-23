package com.protoevo.ui.nn;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.protoevo.biology.evolution.GeneExpressionFunction;
import com.protoevo.biology.nn.ActivationFn;
import com.protoevo.biology.nn.GRNTag;
import com.protoevo.biology.nn.Neuron;
import com.protoevo.biology.nodes.SurfaceNode;
import com.protoevo.utils.Utils;

public class MouseOverNeuronCallback {
    private final GlyphLayout layout = new GlyphLayout();
    private final BitmapFont font;
    private GeneExpressionFunction geneExpressionFunction;

    public MouseOverNeuronCallback(BitmapFont font) {
        this.font = font;
    }

    public void setGeneExpressionFunction(GeneExpressionFunction geneExpressionFunction) {
        this.geneExpressionFunction = geneExpressionFunction;
    }

    public String getSurfaceNodeLabel(Neuron neuron,
                                      GeneExpressionFunction.Node geneNode,
                                      SurfaceNode surfaceNode) {
        String label = "Node " + surfaceNode.getIndex() + ":";
        if (surfaceNode.getAttachment() != null)
            label += " " + surfaceNode.getAttachmentName();

        String displayName = geneNode.getDisplayName();
        if (displayName.contains(SurfaceNode.activationPrefix)) {
            String[] parts = displayName.split(SurfaceNode.activationPrefix);
            label += " " + getAttachmentIOString(surfaceNode, parts);
        } else {
            label += " " + displayName;
        }
        label += " = " + Utils.numberToString(neuron.getLastState(), 2);
        return label;
    }

    private String getAttachmentIOString(SurfaceNode surfaceNode, String[] parts) {
        int idx = Integer.parseInt(parts[1]);
        boolean isInput = parts[0].contains("Input");
        String dir = isInput ? "Input" : "Output";
        if (surfaceNode.getAttachment() != null) {
            String meaning;
            if (isInput)
                meaning = surfaceNode.getAttachment().getInputMeaning(idx);
            else
                meaning = surfaceNode.getAttachment().getOutputMeaning(idx);
            return meaning == null ? "Unused" : meaning;
        } else {
            return dir + " " + idx;
        }
    }

    public String getNeuronLabel(Neuron neuron) {

        if (geneExpressionFunction != null && neuron.getTags() != null
                && neuron.getTags().length > 0 && neuron.getTags()[0] instanceof GRNTag)
        {
            Object tagged = ((GRNTag) neuron.getTags()[0]).apply(geneExpressionFunction);
            if (tagged instanceof GeneExpressionFunction.Node) {
                GeneExpressionFunction.Node node = (GeneExpressionFunction.Node) tagged;
                if (node.getLastTarget() instanceof SurfaceNode) {
                    return getSurfaceNodeLabel(neuron, node, (SurfaceNode) node.getLastTarget());
                }
            }
        }

        if (neuron.hasLabel())
            return neuron.getLabel() + " = " +
                    Utils.numberToString(neuron.getLastState(), 2);

        return neuron.getActivation() + "(z) = "
                + Utils.numberToString(neuron.getLastState(), 2);
    }

    public void apply(SpriteBatch batch, Neuron neuron, float graphicsRadius) {
        float x = neuron.getGraphicsX();
        float y = neuron.getGraphicsY();

        String labelStr = getNeuronLabel(neuron);
        layout.setText(font, labelStr);
        float labelX = x - layout.width / 2;
        float pad = font.getLineHeight() * 0.3f;
        float infoWidth = layout.width + 2*pad;
        if (labelX + infoWidth >= Gdx.graphics.getWidth())
            labelX = (int) (Gdx.graphics.getWidth() - 1.1 * infoWidth);

        float labelY = (y + 1.1f * graphicsRadius) + font.getLineHeight();
        font.draw(batch, labelStr, labelX, labelY);
    }
}
