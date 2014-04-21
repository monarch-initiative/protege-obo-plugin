package org.protege.oboeditor.renderer;

import org.coode.string.EscapeUtils;
import org.protege.editor.owl.OWLEditorKit;
import org.protege.editor.owl.model.OWLModelManager;
import org.protege.editor.owl.ui.renderer.layout.*;
import org.protege.oboeditor.frames.AbstractDatabaseCrossReferenceList;
import org.protege.oboeditor.util.OBOVocabulary;
import org.semanticweb.owlapi.model.*;

import javax.swing.*;

import java.awt.*;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.*;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author Simon Jupp
 * @date 14/03/2014
 * Functional Genomics Group EMBL-EBI
 */
public class OBOAnnotationCellRenderer extends PageCellRenderer {

    public static final Color ANNOTATION_PROPERTY_FOREGROUND = new Color(65, 108, 226);

    private OWLEditorKit editorKit;

    private Pattern URL_PATTERN = Pattern.compile("\\b(https?|ftp|file)://[-a-zA-Z0-9+&@#/%?=~_|!:,.;]*[-a-zA-Z0-9+&@#/%=~_|]\\b");

    private OWLOntology ontology;

    public OBOAnnotationCellRenderer(OWLEditorKit editorKit) {
        super();
        this.editorKit = editorKit;
    }

    /**
     * Sets a reference ontology to provide a context for the rendering.  The renderer may render certain things differently
     * depending on whether this is equal to the active ontology or not.
     * @param ontology The ontology.
     */
    public void setReferenceOntology(OWLOntology ontology) {
        this.ontology = ontology;
    }

    /**
     * Clears the reference ontology.
     * @see {OWLAnnotationCellRenderer2#setOntology()}
     */
    public void clearReferenceOntology() {
        ontology = null;
    }

    /**
     * Determines if the reference ontology (if set) is equal to the active ontology.
     * @return <code>true</code> if the reference ontology is equal to the active ontology, otherwise <code>false</code>.
     */
    public boolean isReferenceOntologyActive() {
        return ontology != null && ontology.equals(editorKit.getOWLModelManager().getActiveOntology());
    }

    @Override
    protected Object getValueKey(Object value) {
    	List<AnnotationXrefContainer> list = extractOWLAnnotationFromCellValues(value);
//    	if (list.size() == 0) {
//			return list;
//		}
//    	else if (list.size() == 1) {
//    		return list.get(0);
//    	}
        return list.hashCode();
    }


    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    ////
    ////  JTable Cell Rendering
    ////
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////


    @Override
    protected void fillPage(Page page, JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
        Color foreground = isSelected ? table.getSelectionForeground() : table.getForeground();
        Color background = isSelected ? table.getSelectionBackground() : table.getBackground();
        renderCellValue(page, value, foreground, background, isSelected);
    }

    @Override
    protected int getMaxAvailablePageWidth(Page page, JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
        return table.getColumnModel().getColumn(column).getWidth();
    }


    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    ////
    ////  JList Cell Rendering
    ////
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    @Override
    protected void fillPage(final Page page, JList list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
        Color foreground = isSelected ? list.getSelectionForeground() : list.getForeground();
        Color background = isSelected ? list.getSelectionBackground() : list.getBackground();
        renderCellValue(page, value, foreground, background, isSelected);
    }

    @Override
    protected int getMaxAvailablePageWidth(JList list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
        Insets insets = list.getInsets();//OWLFrameList.ITEM_BORDER.getBorderInsets();
        int componentWidth = list.getWidth();
        JViewport vp = (JViewport) SwingUtilities.getAncestorOfClass(JViewport.class, list);
        if(vp != null) {
            componentWidth = vp.getViewRect().width;
        }

        return componentWidth - list.getInsets().left - list.getInsets().right - insets.left + insets.right - 20;
    }




    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    private static class AnnotationXrefContainer {
    	private OWLAnnotation annotation = null;
    	private List<OWLAnnotation> xrefs = null;
    	
    	OWLLiteral asLiteral() {
    		return (OWLLiteral) annotation.getValue();
    	}

		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result
					+ ((annotation == null) ? 0 : annotation.hashCode());
			result = prime * result + ((xrefs == null) ? 0 : xrefs.hashCode());
			return result;
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			if (obj == null)
				return false;
			if (getClass() != obj.getClass())
				return false;
			AnnotationXrefContainer other = (AnnotationXrefContainer) obj;
			if (annotation == null) {
				if (other.annotation != null)
					return false;
			} else if (!annotation.equals(other.annotation))
				return false;
			if (xrefs == null) {
				if (other.xrefs != null)
					return false;
			} else if (!xrefs.equals(other.xrefs))
				return false;
			return true;
		}
		
		static AnnotationXrefContainer create(OWLAnnotation annotation) {
			AnnotationXrefContainer c = new AnnotationXrefContainer();
			c.annotation = annotation;
			return c;
		}
		
		static AnnotationXrefContainer create(OWLAnnotation annotation, Collection<OWLAnnotation> xrefs) {
			AnnotationXrefContainer c = create(annotation);
			if (xrefs != null) {
				c.xrefs = new ArrayList<OWLAnnotation>(xrefs);
			}
			return c;
		}
    }
    

    /**
     * Renderes a list or table cell value if the value contains an OWLAnnotation.
     * @param page The page that the value will be rendered into.
     * @param value The value that may or may not contain an OWLAnnotation.  The annotation will be extracted from
     * this value.
     * @param foreground The default foreground color.
     * @param background The default background color.
     * @param isSelected Whether or not the cell containing the value is selected.
     */
    private void renderCellValue(Page page, Object value, Color foreground, Color background, boolean isSelected) {
        List<AnnotationXrefContainer> annotations = extractOWLAnnotationFromCellValues(value);
        if (annotations != null && !annotations.isEmpty()) {
            renderAnnotationValues(page, annotations, foreground, background, isSelected);
        }
        page.setMargin(2);
        page.setMarginBottom(20);

    }
    
    /**
     * Extracts an OWLAnnotation from the actual value held in a cell in a list or table.
     * @param value The list or table cell value.
     * @return The OWLAnnotation contained within the value.
     */
    protected List<AnnotationXrefContainer> extractOWLAnnotationFromCellValues(Object value) {
    	List<AnnotationXrefContainer> annotations = Collections.emptyList();
        if (value instanceof AbstractDatabaseCrossReferenceList.AnnotationsListItem) {
            OWLAnnotation annotation = ((AbstractDatabaseCrossReferenceList.AnnotationsListItem) value).getAnnotation();
            annotations = Collections.singletonList(AnnotationXrefContainer.create(annotation));
        }
        else if (value instanceof OWLAnnotation) {
        	OWLAnnotation annotation = (OWLAnnotation) value;
        	annotations = Collections.singletonList(AnnotationXrefContainer.create(annotation));
        }
        else if (value instanceof Collection) {
			Collection c = (Collection) value;
        	if (!c.isEmpty()) {
        		annotations = new ArrayList<AnnotationXrefContainer>(c.size());
				for(Object o : c) {
					if (o instanceof OWLAnnotationAssertionAxiom) {
						OWLAnnotationAssertionAxiom ax = (OWLAnnotationAssertionAxiom) o;
						annotations.add(AnnotationXrefContainer.create(ax.getAnnotation(), filterXrefs(ax.getAnnotations())));
					}
					else if (o instanceof OWLAnnotation) {
						annotations.add(AnnotationXrefContainer.create((OWLAnnotation) o));
					}
	        	}
			}
        }
        return annotations;
    }

    
    private List<OWLAnnotation> filterXrefs(Collection<OWLAnnotation> annotations) {
    	List<OWLAnnotation> xrefs = null;
    	if (annotations != null && !annotations.isEmpty()) {
			for (OWLAnnotation annotation : annotations) {
				OWLAnnotationProperty property = annotation.getProperty();
				if (OBOVocabulary.XREF.getIRI().equals(property.getIRI())) {
					if (xrefs == null) {
						xrefs = Collections.singletonList(annotation);
					}
					else if (xrefs.size() == 1) {
						OWLAnnotation prev = xrefs.get(0);
						xrefs = new ArrayList<OWLAnnotation>();
						xrefs.add(prev);
						xrefs.add(annotation);
					}
					else {
						xrefs.add(annotation);
					}
				}
			}
		}
    	return xrefs;
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    /**
     * Formats paragraphs that were generated as a result of rendering an annotation value with global formatting such
     * as tab count and margins.  This ensures that all paragraphs representing annotation values have the correct
     * indentation etc.
     * @param valueRenderingParagraphs The paragraphs to be formatted.
     */
    private void applyGlobalFormattingToAnnotationValueParagraphs(List<Paragraph> valueRenderingParagraphs) {
        for (Paragraph paragraph : valueRenderingParagraphs) {
            paragraph.setTabCount(0);
            paragraph.setMarginBottom(2);
        }
    }

//    /**
//     * Renders the annotation property into a paragraph in the page.
//     * @param page The page to insert the paragraph into.
//     * @param annotation The annotation containing the property to be rendered.
//     * @param defaultForeground The default foreground color.
//     * @param defaultBackground The default background color.
//     * @param isSelected Specifies whether the associated cell is selected or not.
//     */
//    private void renderAnnotationProperty(Page page, OWLAnnotation annotation, Color defaultForeground, Color defaultBackground, boolean isSelected) {
//        OWLAnnotationProperty property = annotation.getProperty();
//        String rendering = editorKit.getOWLModelManager().getRendering(property);
//        Paragraph paragraph = page.addParagraph(rendering);
//        Color foreground = getAnnotationPropertyForeground(defaultForeground, isSelected);
//        paragraph.setForeground(foreground);
////        if (isReferenceOntologyActive()) {
////            paragraph.setBold(true);
////        }
//        if (annotation.getValue() instanceof OWLLiteral) {
//            OWLLiteral literalValue = (OWLLiteral) annotation.getValue();
//            paragraph.append("    ", foreground);
//            appendTag(paragraph, literalValue, foreground, isSelected);
//        }
//        paragraph.setMarginBottom(4);
//    }

//    private Color getAnnotationPropertyForeground(Color defaultForeground, boolean isSelected) {
//        return isSelected ? defaultForeground : ANNOTATION_PROPERTY_FOREGROUND;
//    }

    /**
     * Renders an annotation value into a {@link Page}.
     * @param page The page that the value should be rendered into.
     * @param annotation The annotation that contains the value to be rendered.
     * @param defaultForeground The default foreground color.
     * @param defaultBackground The default background color.
     * @param isSelected Whether or not the cell containing the annotation is selected.
     * @return A list of paragraphs that represent the rendering of the annotation value.  These paragraphs will have
     * been added to the Page specified by the page argument.
     */
    private List<Paragraph> renderAnnotationValues(final Page page, final List<AnnotationXrefContainer> annotations, final Color defaultForeground, final Color defaultBackground, final boolean isSelected) {
    	List<Paragraph> paragraphs;
    	if (annotations.size() == 1) {
	        final AnnotationXrefContainer container = annotations.get(0);
			OWLAnnotationValue annotationValue = container.annotation.getValue();
	        paragraphs = annotationValue.accept(new OWLAnnotationValueVisitorEx<List<Paragraph>>() {
	            public List<Paragraph> visit(IRI iri) {
	                return renderIRI(page, iri, container.xrefs, defaultForeground, defaultBackground, isSelected, hasFocus());
	            }
	
	            public List<Paragraph> visit(OWLAnonymousIndividual individual) {
	                return renderAnonymousIndividual(page, individual, container.xrefs);
	            }
	
	            public List<Paragraph> visit(OWLLiteral literal) {
	                return renderLiteral(page, annotations, defaultForeground, defaultBackground, isSelected);
	            }
	        });
    	}
    	else {
    		final List<AnnotationXrefContainer> literals = new ArrayList<AnnotationXrefContainer>(annotations.size());
    		for(final AnnotationXrefContainer container : annotations) {
    			container.annotation.getValue().accept(new OWLAnnotationValueVisitor() {
					
					@Override
					public void visit(OWLLiteral literal) {
						literals.add(container);
					}
					
					@Override
					public void visit(OWLAnonymousIndividual individual) {
						// do nothing
					}
					
					@Override
					public void visit(IRI iri) {
						// do nothing
					}
				});
    		}
    		paragraphs = renderLiteral(page, literals, defaultForeground, defaultBackground, isSelected);
    	}
        applyGlobalFormattingToAnnotationValueParagraphs(paragraphs);
        return paragraphs;
    }

    /**
     * Renderes an annotation value that is an IRI
     * @param page The page that the value will be rendered into.
     * @param iri The IRI that is the annotation value.
     * @param defaultForeground The default foreground color.
     * @param defaultBackgound The default background color.
     * @param isSelected Whether or not the cell containing the annotation is selected.
     * @param hasFocus Whether or not the cell containing the annotation has the focus.
     * @return A list of paragraphs that represent the rendering of the annotation value.
     */
    private List<Paragraph> renderIRI(Page page, IRI iri, List<OWLAnnotation> xrefs, Color defaultForeground, Color defaultBackgound, boolean isSelected, boolean hasFocus) {
        OWLModelManager modelManager = editorKit.getOWLModelManager();
        Set<OWLEntity> entities = modelManager.getOWLEntityFinder().getEntities(iri);
        List<Paragraph> paragraphs;
        if (entities.isEmpty()) {
            paragraphs = renderExternalIRI(page, iri);
        }
        else {
            paragraphs = renderEntities(page, entities);
        }
        return paragraphs;
    }

    /**
     * Determines whether an IRI that represents an annotation value can be opened in a web browser. i.e. wherther or
     * not the IRI represents a web link.
     * @param iri The iri to be tested.
     * @return <code>true</code> if the IRI represents a web link, other wise <code>false</code>.
     */
    private boolean isLinkableAddress(IRI iri) {
        String scheme = iri.getScheme();
        return scheme != null && scheme.startsWith("http");
    }

    /**
     * Renders an IRI as a full IRI rather than as an IRI that represents an entity in the signature of the imports
     * closure of the active ontology.
     * @param page The page that the IRI should be rendered into.
     * @param iri The IRI to be rendered.
     * @return A list of paragraphs that represent the rendering of the annotation value.
     */
    private List<Paragraph> renderExternalIRI(Page page, IRI iri) {
        Paragraph paragraph;
        if (isLinkableAddress(iri)) {
            paragraph = page.addParagraph(iri.toString(), new HTTPLink(iri.toURI()));
        }
        else {
            paragraph = page.addParagraph(iri.toString());
        }
        return Arrays.asList(paragraph);
    }

    /**
     * Renderes a set of entities as an annotation value.  The idea is that the annotation value is an IRI that
     * corresponds to the IRI of entities in the imports closure of the active ontology.
     * @param page The page that the entities will be rendered into.
     * @param entities The entities.
     * @return A list of paragraphs that represents the rendering of the entities.
     */
    private List<Paragraph> renderEntities(Page page, Set<OWLEntity> entities) {
        List<Paragraph> paragraphs = new ArrayList<Paragraph>();
        for (OWLEntity entity : entities) {
            Icon icon = getIcon(entity);
            OWLModelManager modelManager = editorKit.getOWLModelManager();
            Paragraph paragraph = new Paragraph(modelManager.getRendering(entity), new OWLEntityLink(editorKit, entity));
            paragraph.setIcon(icon);
            page.add(paragraph);
            paragraphs.add(paragraph);
        }
        return paragraphs;
    }

    /**
     * Gets the icon for an entity.
     * @param entity The entity.
     * @return The icon or null if the entity does not have an icon.
     */
    private Icon getIcon(OWLObject entity) {
        return editorKit.getOWLWorkspace().getOWLIconProvider().getIcon(entity);
    }

    /**
     * Renders an annotation value that is an OWLLiteral.
     * @param page The page that the value will be rendered into.
     * @param literal The literal to be rendered.
     * @param foreground The default foreground.
     * @param background The default background.
     * @param isSelected Whether or not the cell containing the annotation value is selected.
     * @return A list of paragraphs that represent the rendering of the literal.
     */
    private List<Paragraph> renderLiteral(Page page, List<AnnotationXrefContainer> literals, Color foreground, Color background, boolean isSelected) {
    	List<Paragraph> result = new ArrayList<Paragraph>();
    	final StringBuilder sb = new StringBuilder();
    	for(AnnotationXrefContainer literal : literals) {
    		OWLLiteral owlLiteral = literal.asLiteral();
	        final String rendering = EscapeUtils.unescapeString(owlLiteral.getLiteral()).trim();
	        if (rendering.length() > 0) {
	        	if (sb.length() > 1) {
					sb.append(", ");
				}
	        	sb.append(rendering);
	        }
	        if (literal.xrefs != null && !literal.xrefs.isEmpty()) {
	        	sb.append('[');
	        	for (Iterator<OWLAnnotation> xrefIt = literal.xrefs.iterator(); xrefIt.hasNext();) {
	        		OWLAnnotation xref = xrefIt.next();
	        		xref.getValue().accept(new OWLAnnotationValueVisitor() {
						
						@Override
						public void visit(OWLLiteral xrefLiteral) {
							sb.append(xrefLiteral.getLiteral());
						}
						
						@Override
						public void visit(OWLAnonymousIndividual individual) {
							// ignore
						}
						
						@Override
						public void visit(IRI iri) {
							// ignore
						}
					});
	        		if (xrefIt.hasNext()) {
						sb.append(", ");
					}
	        	}
	        	sb.append(']');
	        }
    	}
    	if (sb.length() > 0) {
	    	String rendering = sb.toString();
	    	List<LinkSpan> linkSpans = extractLinks(rendering);
	    	Paragraph literalParagraph = new Paragraph(rendering, linkSpans);
	    	literalParagraph.setForeground(foreground);
	    	page.add(literalParagraph);
	    	result.add(literalParagraph);
	    	Paragraph tagParagraph = literalParagraph;//new Paragraph("");
	    	tagParagraph.append("    ", foreground);
	    	page.add(tagParagraph);
	    	result.add(tagParagraph);
	    	tagParagraph.setMarginTop(2);
	    	tagParagraph.setTabCount(2);
//            appendTag(tagParagraph, literal, foreground, isSelected);
    	}
        return result;
    }

//    private void appendTag(Paragraph tagParagraph, OWLLiteral literal, Color foreground, boolean isSelected) {
//        Color tagColor = isSelected ? foreground : Color.GRAY;
//        Color tagValueColor = isSelected ? foreground : Color.GRAY;
//        if (literal.hasLang()) {
//            tagParagraph.append("[language: ", tagColor);
//            tagParagraph.append(literal.getLang(), tagValueColor);
//            tagParagraph.append("]", tagColor);
//        }
//        else if(!literal.isRDFPlainLiteral()) {
//            tagParagraph.append("[type: ", tagColor);
//            tagParagraph.append(editorKit.getOWLModelManager().getRendering(literal.getDatatype()), tagValueColor);
//            tagParagraph.append("]", tagColor);
//        }
////        if (ontology != null) {
////            tagParagraph.append("    ", foreground);
////            tagParagraph.append("[in: ", tagColor);
////            String ontologyRendering = editorKit.getOWLModelManager().getRendering(ontology);
////            tagParagraph.append(ontologyRendering, tagColor);
////            tagParagraph.append("]", tagColor);
////        }
//    }


    /**
     * Extracts links from a piece of text.
     * @param s The string that represents the piece of text.
     * @return A (possibly empty) list of links.
     */
    private List<LinkSpan> extractLinks(String s) {
        Matcher matcher = URL_PATTERN.matcher(s);
        List<LinkSpan> result = new ArrayList<LinkSpan>();
        while (matcher.find()) {
            int start = matcher.start();
            int end = matcher.end();
            String url = s.substring(start, end);
            try {
                result.add(new LinkSpan(new HTTPLink(new URI(url)), new Span(start, end)));
            }
            catch (URISyntaxException e) {
                e.printStackTrace();
            }
        }
        return result;
    }

    /**
     * Renders an annotation value that is an anonymous individual.
     * @param page The page that the individual should be rendered into.
     * @param individual The individual.
     * @return A list of paragraphs that represent the rendering of the individual.
     */
    private List<Paragraph> renderAnonymousIndividual(Page page, OWLAnonymousIndividual individual, List<OWLAnnotation> xrefs) {
        String rendering = editorKit.getOWLModelManager().getRendering(individual);
        Paragraph paragraph = page.addParagraph(rendering);
        paragraph.setIcon(getIcon(individual));
        return Arrays.asList(paragraph);
    }

}