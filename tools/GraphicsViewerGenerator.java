import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class GraphicsViewerGenerator {
    private static final int SCREEN_TEXT_ORIGIN = 24000;
    private static final int TEXT_ORIGIN = 25000;
    private static final int ROW_COUNT = 29;
    private static final int TEXT_WIDTH = 53;
    private static final int PRINT_TEXT_WIDTH = 54;
    private static final String NAME_PLACEHOLDER = "********************!";
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
    private static final int POKE = 0xf4;
    private static final int CODE = 0xaf;
    private static final int LOAD = 0xef;
    private static final int CLEAR = 0xfd;
    private static final int RUN = 0xf7;
    private static final int PRINT = 0xf5;
    private static final int BORDER = 0xe7;
    private static final int PAPER = 0xda;
    private static final int INK = 0xd9;
    private static final int CLS = 0xfb;
    private static final int CHR = 0xc2;
    private static final int LPRINT = 0xe0;

    private GraphicsViewerGenerator() {
    }

    public static void main(String[] args) throws IOException {
        Path output = args.length == 0 ? Path.of("graphics.trd") : Path.of(args[0]);
        List<String> printerText = loadPrinterText();
        Files.write(output, createDisk(basicFile(loadMachineCode(printerText), printerText),
                loadPhoto()));
    }

    private static List<String> loadPrinterText() throws IOException {
        String text = Files.readString(Path.of("invitation.txt"), StandardCharsets.US_ASCII);
        String[] lines = text.split("\r\n|\r|\n", -1);
        int count = lines.length;
        if (count > 0 && lines[count - 1].isEmpty()) {
            count--;
        }
        if (count > ROW_COUNT) {
            throw new IllegalArgumentException("invitation.txt has more than 29 lines");
        }
        List<String> result = new ArrayList<>();
        int nameLines = 0;
        for (int line = 0; line < ROW_COUNT; line++) {
            String value = line < count ? lines[line] : "";
            if (value.length() > TEXT_WIDTH) {
                throw new IllegalArgumentException("invitation.txt line exceeds 53 characters");
            }
            if (value.contains(NAME_PLACEHOLDER)) {
                nameLines++;
            }
            result.add(value);
        }
        if (nameLines != 1) {
            throw new IllegalArgumentException(
                    "invitation.txt needs exactly one name placeholder");
        }
        return result;
    }

    private static byte[] loadPhoto() throws IOException {
        byte[] photo = Files.readAllBytes(Path.of("photo.scr"));
        if (photo.length != 6912) {
            throw new IllegalArgumentException("photo.scr must be exactly 6912 bytes");
        }
        return photo;
    }

    private static byte[] loadMachineCode(List<String> printerText) throws IOException {
        byte[] code = Files.readAllBytes(Path.of("tools/screen-text.bin"));
        byte[] converter = Files.readAllBytes(Path.of("tools/graphics-print.bin"));
        byte[] text = textBytes(printerText);
        byte[] result = new byte[TEXT_ORIGIN - 23872 + text.length];
        System.arraycopy(converter, 0, result, 0, converter.length);
        System.arraycopy(code, 0, result, SCREEN_TEXT_ORIGIN - 23872, code.length);
        System.arraycopy(text, 0, result, TEXT_ORIGIN - 23872, text.length);
        return result;
    }

    private static byte[] textBytes(List<String> printerText) {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        for (String line : printerText) {
            byte[] bytes = line.getBytes(StandardCharsets.US_ASCII);
            output.write(bytes, 0, bytes.length);
            for (int column = line.length(); column < TEXT_WIDTH; column++) {
                output.write(' ');
            }
        }
        return output.toByteArray();
    }

    private static byte[] basicFile(byte[] machineCode, List<String> printerText) {
        ByteArrayOutputStream program = new ByteArrayOutputStream();
        writeLine(program, 1, new BasicLine().token(REM).bytes(machineCode));
        writeLine(program, 10, new BasicLine().token(BORDER).number(7).ascii(":")
                .token(PAPER).number(7).ascii(":").token(INK).number(0).ascii(":")
                .token(CLS));
        writeLine(program, 20, new BasicLine().token(PRINT)
                .ascii("\"Insert disk with photo\":").token(PRINT)
                .ascii("\"Press Enter\":").token(INPUT).ascii("a$"));
        writeLine(program, 30, new BasicLine().token(CLEAR).number(34999).ascii(":")
                .token(LET).ascii("p=").number(SCREEN_TEXT_ORIGIN).ascii(":")
                .token(LET).ascii("g=").number(23872).ascii(":")
                .token(LET).ascii("d=").number(15619));
        writeLine(program, 40, new BasicLine().token(LET).ascii("e=").token(USR)
                .ascii("d:").token(REM).ascii(":").token(LOAD)
                .ascii("\"photo\"").token(CODE));
        writeLine(program, 50, new BasicLine().token(LET).ascii("l=")
                .token(PEEK).number(23784).ascii("+").number(256).ascii("*")
                .token(PEEK).number(23785).ascii(":").token(IF).ascii("l")
                .token(NOT_EQUAL).number(6912).token(THEN).token(PRINT)
                .ascii("\"Photo is not found\":").token(GO_TO).number(20));
        writeLine(program, 60, new BasicLine().token(INPUT).ascii("\"Name (20):\";n$"));
        writeLine(program, 70, new BasicLine().token(IF).ascii("n$=\"\"")
                .token(THEN).token(RUN).number(10));
        writeLine(program, 80, new BasicLine().token(IF).token(LEN).ascii(" n$>")
                .number(20).token(THEN).token(LET).ascii("n$=n$(").token(TO)
                .number(20).ascii(")"));
        writeLine(program, 85, new BasicLine().token(POKE).number(17728).ascii(",")
                .number(255));
        writeLine(program, 90, new BasicLine().token(FOR).ascii("y=").number(0)
                .token(TO).number(21));
        writeLine(program, 100, new BasicLine().token(POKE).ascii("g+").number(3)
                .ascii(",y"));
        writeLine(program, 110, new BasicLine().token(RANDOMIZE).token(USR).ascii("g"));
        writeLine(program, 120, new BasicLine().token(LET).ascii("a=").number(16384)
                .ascii("+y*").number(256));
        writeLine(program, 121, new BasicLine().token(IF).ascii("y>").number(7).token(THEN)
                .token(LET).ascii("a=").number(16384).ascii("+(y-").number(8).ascii(")*")
                .number(256).ascii("+").number(32));
        writeLine(program, 122, new BasicLine().token(IF).ascii("y>").number(15).token(THEN)
                .token(LET).ascii("a=").number(16384).ascii("+(y-").number(16).ascii(")*")
                .number(256).ascii("+").number(64));
        writeLine(program, 123, new BasicLine().token(POKE).ascii("a,").number(255));
        writeLine(program, 130, new BasicLine().token(NEXT).ascii("y"));
        writeLine(program, 245, new BasicLine().token(POKE).number(23681).ascii(",")
                .number(91).ascii(":").token(LPRINT).token(CHR).ascii(" ")
                .number(27).ascii(";\"R1\";").token(CHR).ascii(" ").number(27)
                .ascii(";\"M\";"));
        writeLine(program, 250, new BasicLine().token(FOR).ascii("y=").number(0)
                .token(TO).number(ROW_COUNT - 1));
        writeTextLines(program, 260, printerText, PRINT_TEXT_WIDTH);
        writeLine(program, 300, new BasicLine().token(LPRINT).token(CHR).ascii(" ")
                .number(13).ascii(";").token(CHR).ascii(" ").number(27)
                .ascii(";\"J\";").token(CHR).ascii(" ").number(25).ascii(";t$;"));
        writeLine(program, 310, new BasicLine().token(IF).ascii("y>").number(21)
                .token(THEN).token(GO_TO).number(410));
        writeLine(program, 330, new BasicLine().token(LPRINT).token(CHR).ascii(" ")
                .number(27).ascii(";\"K\";").token(CHR).ascii(" ").number(176)
                .ascii(";").token(CHR).ascii(" ").number(0).ascii(";"));
        writeLine(program, 340, new BasicLine().token(FOR).ascii("x=").number(0)
                .token(TO).number(175));
        writeLine(program, 380, new BasicLine().token(LPRINT).token(CHR).ascii(" ")
                .token(PEEK).ascii(" (").number(40000).ascii("+y*").number(176)
                .ascii("+x);"));
        writeLine(program, 390, new BasicLine().token(NEXT).ascii("x"));
        writeLine(program, 410, new BasicLine().token(NEXT).ascii("y"));
        writeLine(program, 420, new BasicLine().token(LPRINT).token(CHR).ascii(" ")
                .number(13).ascii(";").token(CHR).ascii(" ").number(10).ascii(";"));
        writeLine(program, 520, new BasicLine().token(RUN).number(10));
        program.write(0x80);
        program.write(0xaa);
        program.write(1);
        program.write(0);
        program.write(0x20);
        return program.toByteArray();
    }

    private static void writeTextLines(ByteArrayOutputStream program, int firstLine,
            List<String> printerText, int width) {
        for (int line = 0; line < printerText.size(); line++) {
            BasicLine statement = new BasicLine().token(IF).ascii("y=").number(line)
                    .token(THEN).token(LET).ascii("t$=");
            String text = printerText.get(line);
            int name = text.indexOf(NAME_PLACEHOLDER);
            if (name >= 0) {
                String beforeName = text.substring(0, name);
                String afterName = text.substring(name + NAME_PLACEHOLDER.length());
                String nameLine = "\"" + beforeName + "\"+n$+\"!" + afterName
                        + " ".repeat(width) + "\"";
                statement.ascii(nameLine);
            } else {
                String value = text.substring(0, Math.min(width, text.length()));
                statement.ascii("\"" + value + " ".repeat(width - value.length()) + "\"");
            }
            if (name >= 0) {
                statement.ascii(":").token(LET).ascii("t$=t$(").token(TO).number(width)
                        .ascii(")");
            }
            writeLine(program, firstLine + line, statement);
        }
    }

    private static byte[] createDisk(byte[] viewer, byte[] photo) {
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
        byte[] photoName = "photo   ".getBytes(StandardCharsets.US_ASCII);
        System.arraycopy(photoName, 0, disk, 16, photoName.length);
        disk[24] = 'C';
        putWord(disk, 25, 16384);
        putWord(disk, 27, photo.length);
        int photoSectors = (photo.length + 255) / 256;
        disk[29] = (byte) photoSectors;
        disk[30] = (byte) (sectors % 16);
        disk[31] = (byte) (1 + sectors / 16);
        int used = sectors + photoSectors;
        disk[2048 + 225] = (byte) (used % 16);
        disk[2048 + 226] = (byte) (1 + used / 16);
        disk[2048 + 227] = 0x16;
        disk[2048 + 228] = 2;
        putWord(disk, 2048 + 229, 2544 - used);
        disk[2048 + 231] = 0x10;
        Arrays.fill(disk, 2048 + 233, 2048 + 242, (byte) ' ');
        byte[] label = "GRAPHICS".getBytes(StandardCharsets.US_ASCII);
        System.arraycopy(label, 0, disk, 2048 + 245, label.length);
        System.arraycopy(viewer, 0, disk, 4096, viewer.length);
        System.arraycopy(photo, 0, disk, 4096 + sectors * 256, photo.length);
        return disk;
    }

    private static void putWord(byte[] output, int offset, int value) {
        output[offset] = (byte) value;
        output[offset + 1] = (byte) (value >> 8);
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
}
