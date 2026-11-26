import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public final class ViewerGenerator {
    private static final int ORIGIN = 23872;
    private static final int REM = 0xea;
    private static final int LET = 0xf1;
    private static final int USR = 0xc0;
    private static final int RANDOMIZE = 0xf9;
    private static final int INPUT = 0xee;
    private static final int IF = 0xfa;
    private static final int THEN = 0xcb;
    private static final int GO_TO = 0xec;
    private static final int NOT_EQUAL = 0xc9;
    private static final int PEEK = 0xbe;
    private static final int FOR = 0xeb;
    private static final int TO = 0xcc;
    private static final int LEN = 0xb1;
    private static final int NEXT = 0xf3;
    private static final int CODE = 0xaf;
    private static final int LOAD = 0xef;
    private static final int CHR = 0xc2;
    private static final int BORDER = 0xe7;
    private static final int PAPER = 0xda;
    private static final int INK = 0xd9;
    private static final int CLS = 0xfb;
    private static final int CLEAR = 0xfd;
    private static final int PRINT = 0xf5;
    private static final int LPRINT = 0xe0;
    private static final int RUN = 0xf7;

    private static final String[] PRINTER_TEXT = {
        "uWAVAEMYJ TOWARI]     ",
        "********************! ",
        "                      ",
        "wY PRIGLA[AETESX W    ",
        "kiber-muzej, G. mUROM ",
        "wLADIMIRSKOJ OBL.,    ",
        "PL. kRESTXQNINA 6w, W ",
        "SROK DO 10.01.2035 G. ",
        "(PREDPOLOVITELXNYYJ   ",
        "GOD NASTUPLENIQ       ",
        "TEHNOLOGI^ESKOJ       ",
        "SINGULQRNOSTI), DLQ   ",
        "POGRUVENIQ W ISTORI@  ",
        "WY^ISLITELXNOJ        ",
        "TEHNIKI               ",
        "SOWETSKOGO I RANNEGO  ",
        "ROSSIJSKOGO PERIODA.  ",
        "w SLU^AE              ",
        "NEWOZMOVNOSTI         ",
        "LI^NO QWITXSQ W       ",
        "UKAZANNYJ SROK MOVNO  ",
        "PEREDATX PRIGLA[ENIE  ",
        "RODSTWENNIKAM,        ",
        "DRUZXQM I KOLLEGAM.   ",
        "pRIGLA[ENIE DEJSTWUET ",
        "NA DWA LICA.          ",
        "                      ",
        "oTPE^ATANO NA uwip    ",
        "|LEKTRONIKA ms-6312.  "
    };

    private static final String[][] FONT = {
        {"0000", "0000", "0000", "0000", "0000", "0000"},
        {"0010", "0010", "0010", "0010", "0000", "0010"},
        {"0101", "0101", "0000", "0000", "0000", "0000"},
        {"0101", "0111", "0101", "0101", "0111", "0101"},
        {"0010", "0111", "0100", "0111", "0001", "0111"},
        {"0100", "0100", "0001", "0010", "0100", "0001"},
        {"0010", "0101", "0010", "0110", "1011", "1111"},
        {"0010", "0100", "0000", "0000", "0000", "0000"},
        {"0010", "0100", "0100", "0100", "0100", "0010"},
        {"0100", "0010", "0010", "0010", "0010", "0100"},
        {"0000", "0101", "0010", "0111", "0010", "0101"},
        {"0000", "0010", "0010", "0111", "0010", "0010"},
        {"0000", "0000", "0000", "0000", "0010", "0010"},
        {"0000", "0000", "0000", "0111", "0000", "0000"},
        {"0000", "0000", "0000", "0000", "0110", "0110"},
        {"0001", "0001", "0010", "0010", "0100", "0100"},
        {"0111", "0101", "0101", "0101", "0101", "0111"},
        {"0010", "0110", "0010", "0010", "0010", "0111"},
        {"0010", "0101", "0001", "0010", "0100", "0111"},
        {"0110", "0001", "0110", "0001", "0001", "0110"},
        {"0001", "0011", "0101", "0101", "0111", "0001"},
        {"0111", "0100", "0110", "0001", "0001", "0110"},
        {"0011", "0100", "0110", "0101", "0101", "0010"},
        {"0111", "0001", "0010", "0010", "0100", "0100"},
        {"0111", "0101", "0010", "0101", "0101", "0111"},
        {"0010", "0101", "0101", "0011", "0001", "0110"},
        {"0000", "0000", "0010", "0000", "0000", "0010"},
        {"0000", "0000", "0010", "0000", "0010", "0010"},
        {"0000", "0001", "0010", "0100", "0010", "0001"},
        {"0000", "0000", "0111", "0000", "0111", "0000"},
        {"0000", "0100", "0010", "0001", "0010", "0100"},
        {"0010", "0101", "0001", "0010", "0000", "0010"}
    };

    private ViewerGenerator() {
    }

    public static void main(String[] args) throws IOException {
        Path output = args.length == 0 ? Path.of("viewer.trd") : Path.of(args[0]);
        Files.write(output, createDisk(create()));
    }

    private static byte[] create() {
        Assembler first = new Assembler(ORIGIN, null);
        emitMachineCode(first);
        Assembler second = new Assembler(ORIGIN, first.labels);
        emitMachineCode(second);
        return basicFile(second.output.toByteArray());
    }

    private static byte[] createDisk(byte[] viewer) {
        byte[] disk = new byte[655360];
        byte[] name = "boot    ".getBytes(StandardCharsets.US_ASCII);
        System.arraycopy(name, 0, disk, 0, name.length);
        disk[8] = 'B';
        int programLength = viewer.length - 5;
        putWord(disk, 9, programLength);
        putWord(disk, 11, programLength);
        int sectors = (viewer.length + 255) / 256;
        disk[13] = (byte) sectors;
        disk[15] = 1;
        disk[2048 + 225] = (byte) (sectors % 16);
        disk[2048 + 226] = (byte) (1 + sectors / 16);
        disk[2048 + 227] = 0x16;
        disk[2048 + 228] = 1;
        putWord(disk, 2048 + 229, 2544 - sectors);
        disk[2048 + 231] = 0x10;
        Arrays.fill(disk, 2048 + 233, 2048 + 242, (byte) ' ');
        byte[] label = "VIEWER".getBytes(StandardCharsets.US_ASCII);
        System.arraycopy(label, 0, disk, 2048 + 245, label.length);
        System.arraycopy(viewer, 0, disk, 4096, viewer.length);
        return disk;
    }

    private static void putWord(byte[] output, int offset, int value) {
        output[offset] = (byte) value;
        output[offset + 1] = (byte) (value >> 8);
    }

    private static byte[] basicFile(byte[] machineCode) {
        ByteArrayOutputStream program = new ByteArrayOutputStream();
        writeLine(program, 1, new BasicLine().token(REM).bytes(machineCode));
        writeLine(program, 10, new BasicLine().token(LET).ascii("p=").number(ORIGIN));
        writeLine(program, 20, new BasicLine()
                .token(BORDER).number(7).ascii(":")
                .token(PAPER).number(7).ascii(":")
                .token(INK).number(0).ascii(":").token(CLS));
        writeLine(program, 30, new BasicLine().token(PRINT)
                .ascii("\"Insert disk with photo\":").token(PRINT)
                .ascii("\"Press Enter\":").token(INPUT).ascii("a$"));
        writeLine(program, 40, new BasicLine().token(CLEAR).number(29999).ascii(":")
                .token(LET).ascii("p=").number(ORIGIN).ascii(":")
                .token(LET).ascii("d=").number(15619));
        writeLine(program, 50, new BasicLine()
                .token(LET).ascii("e=").token(USR).ascii("d:").token(REM).ascii(":")
                .token(LOAD).ascii("\"photo\"").token(CODE));
        writeLine(program, 55, new BasicLine()
                .token(LET).ascii("l=").token(PEEK).number(23784).ascii("+")
                .number(256).ascii("*").token(PEEK).number(23785));
        writeLine(program, 57, new BasicLine().token(IF).ascii("l").token(NOT_EQUAL)
                .number(1711).token(THEN).token(PRINT).ascii("\"Photo is not found\":")
                .token(GO_TO).number(30));
        writeLine(program, 60, new BasicLine().token(RANDOMIZE).token(USR).ascii("p"));
        writeLine(program, 140, new BasicLine().token(INPUT)
                .ascii("\"Name (20):\";n$"));
        writeLine(program, 150, new BasicLine()
                .token(IF).ascii("n$=\"\"").token(THEN).token(RUN));
        writeLine(program, 160, new BasicLine().token(IF).token(LEN)
                .ascii(" n$>").number(20).token(THEN).token(LET)
                .ascii("n$=n$(").token(TO).number(20).ascii(")"));
        writeLine(program, 165, new BasicLine().token(LPRINT).token(CHR)
                .ascii(" ").number(27).ascii(";\"R1\";").token(CHR).ascii(" ")
                .number(27).ascii(";\"M\";"));
        writeLine(program, 170, new BasicLine().token(FOR).ascii("y=").number(1)
                .token(TO).number(29));
        for (int line = 1; line <= 29; line++) {
            BasicLine statement = new BasicLine().token(IF).ascii("y=").number(line)
                    .token(THEN).token(LET).ascii("t$=");
            if (line == 2) {
                statement.ascii("n$+\"                    \":").token(LET)
                        .ascii("t$=t$(").token(TO).number(20).ascii(")+\"! \"");
            } else {
                statement.ascii("\"" + PRINTER_TEXT[line - 1] + "\"");
            }
            writeLine(program, 179 + line, statement);
        }
        writeLine(program, 300, new BasicLine().token(LPRINT).ascii("t$;"));
        writeLine(program, 310, new BasicLine().token(FOR).ascii("x=").number(0)
                .token(TO).number(57));
        writeLine(program, 320, new BasicLine().token(LPRINT).token(CHR).ascii(" ")
                .token(PEEK).ascii(" (").number(30000).ascii("+(y-").number(1)
                .ascii(")*").number(59).ascii("+x);"));
        writeLine(program, 330, new BasicLine().token(NEXT).ascii("x"));
        writeLine(program, 340, new BasicLine().token(LPRINT));
        writeLine(program, 350, new BasicLine().token(NEXT).ascii("y"));
        writeLine(program, 360, new BasicLine().token(RUN).number(10));
        program.write(0x80);
        program.write(0xaa);
        program.write(1);
        program.write(0);
        program.write(0x20);
        return program.toByteArray();
    }

    private static void writeLine(ByteArrayOutputStream program, int number, BasicLine line) {
        byte[] bytes = line.output.toByteArray();
        program.write(number >> 8);
        program.write(number);
        int length = bytes.length + 1;
        program.write(length);
        program.write(length >> 8);
        program.write(bytes, 0, bytes.length);
        program.write(0x0d);
    }

    private static void emitMachineCode(Assembler a) {
        a.label("entry");
        a.emit(0xc3);
        a.wordLabel("renderText");
        a.label("character");
        a.emit(0);
        a.label("renderText");
        a.emit(0xaf, 0x32);
        a.wordLabel("row");
        a.emit(0x32);
        a.wordLabel("column");
        a.emit(0x21, 0x30, 0x75, 0x22);
        a.wordLabel("textPointer");
        a.emit(0x21, 0xaf, 0x06, 0x22);
        a.wordLabel("textRemaining");
        a.label("renderNext");
        a.emit(0x2a);
        a.wordLabel("textRemaining");
        a.emit(0x7c, 0xb5, 0xca);
        a.wordLabel("renderDone");
        a.emit(0x2a);
        a.wordLabel("textPointer");
        a.emit(0x7e, 0x23, 0x22);
        a.wordLabel("textPointer");
        a.emit(0x32);
        a.wordLabel("character");
        a.emit(0xcd);
        a.wordLabel("main");
        a.emit(0x2a);
        a.wordLabel("textRemaining");
        a.emit(0x2b, 0x22);
        a.wordLabel("textRemaining");
        a.emit(0xc3);
        a.wordLabel("renderNext");
        a.label("renderDone");
        a.emit(0xc9);

        a.label("main");
        a.emit(0xdd, 0xe5, 0x3a);
        a.wordLabel("character");
        a.emit(0xfe, 13, 0xca);
        a.wordLabel("newline");
        a.emit(0xfe, 32, 0xda);
        a.wordLabel("done");
        a.emit(0xfe, 64, 0xd2);
        a.wordLabel("done");
        a.emit(0xd6, 32, 0x6f, 0x26, 0, 0x29, 0x54, 0x5d, 0x29, 0x19, 0x11);
        a.wordLabel("font");
        a.emit(0x19, 0xe5, 0xdd, 0xe1, 0x3a);
        a.wordLabel("row");
        a.emit(0x87, 0x6f, 0x26, 0, 0x11);
        a.wordLabel("rowTable");
        a.emit(0x19, 0x5e, 0x23, 0x56, 0xeb, 0x22);
        a.wordLabel("linePointer");
        a.emit(0x3a);
        a.wordLabel("column");
        a.emit(0x6f, 0x26, 0, 0x54, 0x5d, 0x29, 0x19, 0x7d, 0xe6, 7, 0x32);
        a.wordLabel("shift");
        a.emit(0xcb, 0x3c, 0xcb, 0x1d, 0xcb, 0x3c, 0xcb, 0x1d);
        a.emit(0xcb, 0x3c, 0xcb, 0x1d, 0x7d, 0x32);
        a.wordLabel("byteOffset");
        for (int row = 0; row < 6; row++) {
            emitGlyphRow(a, row);
        }
        a.emit(0x3a);
        a.wordLabel("column");
        a.emit(0x3c, 0x32);
        a.wordLabel("column");
        a.relative(0x18, "done");
        a.label("newline");
        a.emit(0xaf, 0x32);
        a.wordLabel("column");
        a.emit(0x3a);
        a.wordLabel("row");
        a.emit(0x3c, 0xfe, 29);
        a.relative(0x38, "storeRow");
        a.emit(0x3e, 28);
        a.label("storeRow");
        a.emit(0x32);
        a.wordLabel("row");
        a.label("done");
        a.emit(0xdd, 0xe1, 0xc9);
        a.label("textPointer");
        a.word(0);
        a.label("textRemaining");
        a.word(0);
        a.label("linePointer");
        a.word(0);
        a.label("shift");
        a.emit(0);
        a.label("byteOffset");
        a.emit(0);
        a.label("row");
        a.emit(0);
        a.label("column");
        a.emit(0);
        emitFont(a);
        emitLineTable(a);
        emitRowTable(a);
    }

    private static void emitGlyphRow(Assembler a, int row) {
        a.emit(0x2a);
        a.wordLabel("linePointer");
        a.emit(0x5e, 0x23, 0x56, 0x23, 0x22);
        a.wordLabel("linePointer");
        a.emit(0x3a);
        a.wordLabel("byteOffset");
        a.emit(0x83, 0x5f);
        a.relative(0x30, "noCarry" + row);
        a.emit(0x14);
        a.label("noCarry" + row);
        a.emit(0xdd, 0x7e, 0, 0xdd, 0x23, 0xe6, 7);
        a.emit(0x07, 0x07, 0x07, 0x07, 0x07, 0x67, 0x2e, 0, 0x3a);
        a.wordLabel("shift");
        a.emit(0x47, 0xb7);
        a.relative(0x28, "aligned" + row);
        a.label("shiftGlyph" + row);
        a.emit(0xcb, 0x3c, 0xcb, 0x1d);
        a.relative(0x10, "shiftGlyph" + row);
        a.label("aligned" + row);
        a.emit(0x1a, 0xb4, 0x12, 0x7d, 0xb7);
        a.relative(0x28, "secondPixelDone" + row);
        a.emit(0x13, 0x1a, 0xb5, 0x12);
        a.label("secondPixelDone" + row);
    }

    private static void emitFont(Assembler a) {
        a.label("font");
        for (String[] glyph : FONT) {
            for (String row : glyph) {
                a.emit(Integer.parseInt(row.substring(1), 2));
            }
        }
    }

    private static void emitLineTable(Assembler a) {
        a.label("lineTable");
        for (int y = 0; y < 192; y++) {
            int address = 0x4000 | ((y & 0xc0) << 5) | ((y & 7) << 8)
                    | ((y & 0x38) << 2);
            a.word(address);
        }
    }

    private static void emitRowTable(Assembler a) {
        a.label("rowTable");
        for (int row = 0; row < 29; row++) {
            a.wordLabel("lineTable", row * 12);
        }
    }

    private static final class BasicLine {
        private final ByteArrayOutputStream output = new ByteArrayOutputStream();

        private BasicLine token(int value) {
            output.write(value);
            return this;
        }

        private BasicLine ascii(String value) {
            byte[] bytes = value.getBytes(StandardCharsets.US_ASCII);
            output.write(bytes, 0, bytes.length);
            return this;
        }

        private BasicLine number(int value) {
            ascii(Integer.toString(value));
            output.write(0x0e);
            output.write(0);
            output.write(0);
            output.write(value);
            output.write(value >> 8);
            output.write(0);
            return this;
        }

        private BasicLine bytes(byte[] values) {
            output.write(values, 0, values.length);
            return this;
        }
    }

    private static final class Assembler {
        private final int origin;
        private final Map<String, Integer> labels = new HashMap<>();
        private final ByteArrayOutputStream output = new ByteArrayOutputStream();

        private Assembler(int origin, Map<String, Integer> previousLabels) {
            this.origin = origin;
            if (previousLabels != null) {
                labels.putAll(previousLabels);
            }
        }

        private void label(String name) {
            labels.put(name, origin + output.size());
        }

        private void emit(int... values) {
            for (int value : values) {
                output.write(value);
            }
        }

        private void word(int value) {
            emit(value, value >> 8);
        }

        private void wordLabel(String name) {
            wordLabel(name, 0);
        }

        private void wordLabel(String name, int displacement) {
            Integer address = labels.get(name);
            word(address == null ? 0 : address + displacement);
        }

        private void relative(int opcode, String name) {
            emit(opcode);
            Integer target = labels.get(name);
            if (target == null) {
                emit(0);
                return;
            }
            int next = origin + output.size() + 1;
            int distance = target - next;
            if (distance < -128 || distance > 127) {
                throw new IllegalArgumentException("Relative jump is out of range");
            }
            emit(distance);
        }
    }
}
