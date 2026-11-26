import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

public final class GraphicsDebugGenerator {
    private static final int REM = 0xea;
    private static final int LET = 0xf1;
    private static final int USR = 0xc0;
    private static final int RANDOMIZE = 0xf9;
    private static final int FOR = 0xeb;
    private static final int TO = 0xcc;
    private static final int NEXT = 0xf3;
    private static final int POKE = 0xf4;
    private static final int CODE = 0xaf;
    private static final int LOAD = 0xef;
    private static final int CLEAR = 0xfd;
    private static final int PAUSE = 0xf2;
    private static final int STOP = 0xe2;
    private static final int PRINT = 0xf5;
    private static final int IF = 0xfa;
    private static final int THEN = 0xcb;
    private static final int NOT_EQUAL = 0xc9;
    private static final int PEEK = 0xbe;
    private static final int INT = 0xba;
    private static final int AND = 0xc6;
    private static final int OR = 0xc5;

    private GraphicsDebugGenerator() {
    }

    public static void main(String[] args) throws IOException {
        byte[] screen = readScreen(Path.of("I:/photo.trd"));
        byte[] code = Files.readAllBytes(Path.of("tools/graphics-print.bin"));
        Path output = args.length == 0 ? Path.of("graphics-debug.trd") : Path.of(args[0]);
        Files.write(output, createDisk(code, screen));
    }

    private static byte[] readScreen(Path path) throws IOException {
        byte[] disk = Files.readAllBytes(path);
        int length = word(disk, 11);
        if (length != 6912) {
            throw new IllegalArgumentException("photo.trd does not contain a Spectrum screen");
        }
        int offset = disk[15] * 4096 + disk[14] * 256;
        return Arrays.copyOfRange(disk, offset, offset + length);
    }

    private static byte[] createDisk(byte[] code, byte[] screen) {
        byte[] disk = new byte[655360];
        byte[] viewer = basicFile(code);
        int bootSectors = (viewer.length + 255) / 256;
        writeEntry(disk, 0, "boot    ", 'B', viewer.length - 5, viewer.length - 5);
        writeEntry(disk, 16, "photo   ", 'C', screen.length, 16384);
        disk[13] = (byte) bootSectors;
        disk[29] = 27;
        disk[14] = 0;
        disk[15] = 1;
        disk[30] = (byte) (bootSectors % 16);
        disk[31] = (byte) (1 + bootSectors / 16);
        int used = bootSectors + 27;
        disk[2048 + 225] = (byte) (used % 16);
        disk[2048 + 226] = (byte) (1 + used / 16);
        disk[2048 + 227] = 0x16;
        disk[2048 + 228] = 2;
        putWord(disk, 2048 + 229, 2544 - used);
        disk[2048 + 231] = 0x10;
        Arrays.fill(disk, 2048 + 233, 2048 + 242, (byte) ' ');
        System.arraycopy(viewer, 0, disk, 4096, viewer.length);
        System.arraycopy(screen, 0, disk, 4096 + bootSectors * 256, screen.length);
        return disk;
    }

    private static byte[] basicFile(byte[] code) {
        ByteArrayOutputStream program = new ByteArrayOutputStream();
        writeLine(program, 1, new BasicLine().token(REM).bytes(code));
        writeLine(program, 10, new BasicLine().token(CLEAR).number(29999).ascii(":")
                .token(LET).ascii("d=").number(15619));
        writeLine(program, 20, new BasicLine().token(LET).ascii("e=").token(USR)
                .ascii("d:").token(REM).ascii(":").token(LOAD)
                .ascii("\"photo\"").token(CODE));
        writeLine(program, 30, new BasicLine().token(POKE).number(23875).ascii(",")
                .number(21).ascii(":").token(RANDOMIZE).token(USR).number(23872));
        writeLine(program, 40, new BasicLine().token(FOR).ascii("i=").number(0)
                .token(TO).number(175));
        writeLine(program, 50, new BasicLine().token(LET).ascii("a=").number(16384)
                .ascii("+").token(INT).ascii(" (i/").number(64).ascii(")*")
                .number(2048).ascii("+").token(INT).ascii(" ((i-").number(64)
                .ascii("*").token(INT).ascii(" (i/").number(64).ascii("))/")
                .number(8).ascii(")*").number(32).ascii("+(i-")
                .number(8).ascii("*").token(INT).ascii(" (i/").number(8)
                .ascii("))*").number(256));
        writeLine(program, 60, new BasicLine().token(POKE).ascii("a,").token(PEEK)
                .ascii(" (").number(40000).ascii("+i)"));
        writeLine(program, 70, new BasicLine().token(NEXT).ascii("i"));
        writeLine(program, 80, new BasicLine().token(PAUSE).number(0));
        program.write(0x80);
        program.write(0xaa);
        program.write(1);
        program.write(0);
        program.write(0x20);
        return program.toByteArray();
    }

    private static void writeEntry(byte[] disk, int offset, String name, int type, int length,
            int address) {
        byte[] bytes = name.getBytes(StandardCharsets.US_ASCII);
        System.arraycopy(bytes, 0, disk, offset, bytes.length);
        disk[offset + 8] = (byte) type;
        putWord(disk, offset + 9, address);
        putWord(disk, offset + 11, length);
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

    private static int word(byte[] data, int offset) {
        return (data[offset] & 0xff) | ((data[offset + 1] & 0xff) << 8);
    }

    private static void putWord(byte[] output, int offset, int value) {
        output[offset] = (byte) value;
        output[offset + 1] = (byte) (value >> 8);
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
