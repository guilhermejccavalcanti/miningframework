package util;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.Modifier;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.BodyDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.nodeTypes.NodeWithAnnotations;
import com.github.javaparser.ast.nodeTypes.NodeWithModifiers;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

public class JavaEquivalenceChecker {

    public static void main(String[] args) throws Exception {
        File a = new File("/home/gjcc/dev/a.java");
        File b = new File("/home/gjcc/dev/b.java");
        System.out.println(JavaEquivalenceChecker.areJavaFilesEquivalent(a.toPath(), b.toPath()));
    }

    public static boolean areJavaFilesEquivalent(Path a, Path b) {
        try{
            String aContent = new String(Files.readAllBytes(a));
            String bContent = new String(Files.readAllBytes(b));
            return JavaEquivalenceChecker.areJavaFilesEquivalent(aContent, bContent);
        } catch (IOException e) {
            return false;
        }
    }

    public static boolean areJavaFilesEquivalent(String a, String b) {
        try {
            //First try a textual comparison (case-sensitive: Java is case-sensitive)
            if (normalizeText(a).equals(normalizeText(b))) {
                return true;
            } else {
                //Otherwise try a syntactic comparison
                CompilationUnit cu1 = StaticJavaParser.parse(a);
                CompilationUnit cu2 = StaticJavaParser.parse(b);

                removeAllComments(cu1);
                removeAllComments(cu2);

                normalizeCompilationUnit(cu1);
                normalizeCompilationUnit(cu2);

                if (cu1.equals(cu2)) {
                    return true;
                } else {
                    return false;
                }
            }
        } catch (Exception e) {
            return false;
        }
    }

    private static void removeAllComments(Node node) {
        node.getAllContainedComments().forEach(Node::remove);
    }

    private static void normalizeCompilationUnit(CompilationUnit cu) {
        normalizeImports(cu);

        for (TypeDeclaration<?> type : cu.getTypes()) {
            normalizeTypeRecursively(type);
        }
    }

    /**
     * Import order is irrelevant to the meaning of a compilation unit, so imports are
     * sorted into a canonical order before comparing.
     */
    private static void normalizeImports(CompilationUnit cu) {
        List<ImportDeclaration> sortedImports = cu.getImports().stream()
                .sorted(Comparator.comparing(JavaEquivalenceChecker::normalizeText))
                .collect(Collectors.toList());
        cu.getImports().clear();
        cu.getImports().addAll(sortedImports);
    }

    private static void normalizeTypeRecursively(TypeDeclaration<?> type) {
        normalizeModifiersAndAnnotations(type);

        // 1) Normalize every member FIRST (modifiers, annotations, nested types and
        //    anonymous class bodies), so the textual sort key is already canonical.
        for (BodyDeclaration<?> member : type.getMembers()) {
            normalizeMemberRecursively(member);
        }

        // 2) Only then sort the members.
        sortMembers(type.getMembers());
    }

    private static void normalizeMemberRecursively(BodyDeclaration<?> member) {
        normalizeModifiersAndAnnotations(member);

        if (member instanceof TypeDeclaration) {
            // Nested type: its own members (and any anonymous classes inside them)
            // are handled by the recursive call.
            normalizeTypeRecursively((TypeDeclaration<?>) member);
            return;
        }

        for (ObjectCreationExpr oce : member.findAll(ObjectCreationExpr.class)) {
            oce.getAnonymousClassBody().ifPresent(anonymBody -> {
                for (BodyDeclaration<?> anonymMember : anonymBody) {
                    normalizeMemberRecursively(anonymMember);
                }
                sortMembers(anonymBody);
            });
        }
    }

    private static void sortMembers(NodeList<BodyDeclaration<?>> members) {
        List<BodyDeclaration<?>> sortedMembers = members.stream()
                .sorted(Comparator.comparing(JavaEquivalenceChecker::normalizeText))
                .collect(Collectors.toList());
        members.clear();
        members.addAll(sortedMembers);
    }

    private static void normalizeModifiersAndAnnotations(BodyDeclaration<?> body) {
        if (body instanceof NodeWithModifiers) {
            NodeWithModifiers<?> nwm = (NodeWithModifiers<?>) body;
            List<Modifier> modifiers = new ArrayList<>(nwm.getModifiers());
            modifiers.sort(Comparator.comparing(m -> m.getKeyword().asString()));
            nwm.setModifiers(new NodeList<>()); // Limpa todos
            for (Modifier m : modifiers) {
                nwm.addModifier(m.getKeyword());
            }
        }

        if (body instanceof NodeWithAnnotations) {
            NodeWithAnnotations<?> nwa = (NodeWithAnnotations<?>) body;
            List<AnnotationExpr> annotations = new ArrayList<>(nwa.getAnnotations());
            annotations.sort(Comparator.comparing(a -> a.getNameAsString()));
            nwa.getAnnotations().clear();
            for (AnnotationExpr ann : annotations) {
                nwa.addAnnotation(ann);
            }
        }
    }

    private static String normalizeText(Node node) {
        String raw = node.toString();
        return normalizeText(raw);
    }

    private static String normalizeText(String txt) {
        return txt.replaceAll("\\s+", "");
    }
}
