package com.github.sszuev;

import com.github.sszuev.ontapi.Formats;
import org.apache.commons.cli.*;
import org.apache.commons.lang.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import ru.avicomp.ontapi.OntFormat;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Container for programs input.
 * <p>
 * Created by @szuev on 12.01.2018.
 */
public class Args {
    private static final String JAR_NAME = "ont-converter.jar";
    private final Path input, output;
    private final OntFormat format;
    private final boolean spin, force, refine, verbose, webAccess;

    private boolean outDir, inDir;

    private Args(Path input, Path output, OntFormat format, boolean spin, boolean force, boolean clear, boolean verbose, boolean webAccess) {
        this.input = input;
        this.output = output;
        this.format = format;
        this.spin = spin;
        this.force = force;
        this.refine = clear;
        this.verbose = verbose;
        this.webAccess = webAccess;
        this.outDir = Files.isDirectory(output);
        this.inDir = Files.isDirectory(input);
    }

    public static Args parse(String... args) throws IOException, IllegalArgumentException {
        Options opts = new Options();
        // optional:
        opts.addOption(Option.builder("h").longOpt("help")
                .desc("Print usage.").build());
        opts.addOption(Option.builder("v").longOpt("verbose")
                .desc("To print info to stdout.").build());
        opts.addOption(Option.builder("w").longOpt("web")
                .desc("Allow web/ftp diving to retrieve dependent ontologies from owl:imports, " +
                        "otherwise use only specified files as the only source.").build());
        opts.addOption(Option.builder("e").longOpt("force") // todo: missing imports
                .desc("Ignore exceptions while loading/saving.").build());
        opts.addOption(Option.builder("s").longOpt("spin")
                .desc("Use spin transformation to replace rdf:List based spin-constructs with their text-literal representation.").build());
        opts.addOption(Option.builder("r").longOpt("refine")
                .desc("Refine output: the resulting ontologies will consist only of the OWL2-DL components.").build());
        // required:
        opts.addOption(Option.builder("i").longOpt("input").hasArgs().required().argName("path")
                .desc("Ontology file or directory containing ontologies to read.\n" +
                        "File(s) must be in one of the following formats:\n" +
                        OntFormat.formats().filter(OntFormat::isReadSupported).map(Enum::name).collect(Collectors.joining(", ")) + "\n" +
                        "- Required.").build());
        opts.addOption(Option.builder("o").longOpt("output").hasArgs().required().argName("path")
                .desc("Ontology file or directory containing ontologies to write\n" +
                        "- Required.").build());
        opts.addOption(Option.builder("f").longOpt("format").hasArgs().required().argName("format")
                .desc("The format of output ontology/ontologies.\n" +
                        "Must be one of the following:\n" +
                        OntFormat.formats().filter(OntFormat::isWriteSupported).map(Enum::name).collect(Collectors.joining(", ")) + "\n" +
                        "- Required.").build());
        if (Stream.of("-h", "--help", "-help", "/?").anyMatch(h -> ArrayUtils.contains(args, h))) {
            throw new UsageException(help(opts, true), 0);
        }
        CommandLine cmd;
        try {
            cmd = new DefaultParser().parse(opts, args);
        } catch (ParseException e) {
            throw new UsageException(e.getLocalizedMessage() + "\n" + help(opts, false), -1);
        }
        // parse
        OntFormat format = Formats.find(cmd.getOptionValue("format"));
        if (!format.isWriteSupported()) {
            throw new IllegalArgumentException(format + " is not suitable for writing.");
        }
        Path in = Paths.get(cmd.getOptionValue("input")).toRealPath();
        Path out = Paths.get(cmd.getOptionValue("output"));

        if (out.getParent() != null) {
            if (!Files.exists(out.getParent())) {
                throw new IllegalArgumentException("Directory " + out.getParent() + " does not exist.");
            }
            out = out.getParent().toRealPath().resolve(out.getFileName());
        }

        if (Files.isDirectory(in) && Files.walk(in).filter(f -> Files.isRegularFile(f)).count() > 1) {
            // out should be directory
            if (Files.exists(out)) {
                if (!Files.isDirectory(out)) {
                    throw new IllegalArgumentException("Output parameter is not a directory path: " + out);
                } else {
                    out = out.toRealPath();
                }
            } else {
                Files.createDirectory(out);
            }
        }
        return new Args(in, out, format, cmd.hasOption("s"), cmd.hasOption("force"), cmd.hasOption("r"), cmd.hasOption("v"), cmd.hasOption("w"));
    }

    private static String help(Options opts, boolean whole) {
        StringBuilder sb = new StringBuilder();
        if (whole) {
            sb.append("A simple command-line utility to convert any rdf graph to OWL2-DL ontology.").append("\n");
        }
        StringWriter sw = new StringWriter();
        new HelpFormatter().printHelp(new PrintWriter(sw), 74, "java -jar " + JAR_NAME, "options:", opts, 1, 3, null, true);
        sb.append(sw);
        if (whole) {
            sb.append("formats aliases (case insensitive):").append("\n");
            OntFormat.formats()
                    .filter(f -> f.isReadSupported() || f.isWriteSupported())
                    .map(f -> Formats.aliases(f).stream().collect(Collectors.joining("|", " " + StringUtils.rightPad(f.name(), 20) + "\t", "")))
                    .forEach(x -> sb.append(x).append("\n"));
        }
        return sb.toString();
    }

    public boolean verbose() {
        return verbose;
    }

    public boolean refine() {
        return refine;
    }

    public boolean spin() {
        return spin;
    }

    public boolean force() {
        return force;
    }

    public boolean web() {
        return webAccess;
    }

    public boolean isInputDirectory() {
        return inDir;
    }

    public Path getInput() {
        return input;
    }

    public boolean isOutputDirectory() {
        return outDir;
    }

    public Path getOutput() {
        return output;
    }

    public OntFormat getOntFormat() {
        return format;
    }

    public String asString() {
        return String.format("Arguments:%n" +
                        "\tinput %s=%s%n" +
                        "\toutput %s=%s%n" +
                        "\tformat=%s%n" +
                        "\tverbose=%s%n" +
                        "\tforce=%s%n" +
                        "\twebAccess=%s%n" +
                        "\trefine=%s%n" +
                        "\tspin=%s%n",
                inDir ? "dir" : "file", input,
                outDir ? "dir" : "file", output,
                format,
                verbose, force, webAccess, refine, spin);
    }

    public static class UsageException extends IllegalArgumentException {
        private final int code;

        UsageException(String s, int code) {
            super(s);
            this.code = code;
        }

        public int code() {
            return code;
        }
    }
}
