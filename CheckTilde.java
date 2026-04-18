public class CheckTilde {
    public static void main(String[] args) throws Exception {
        java.nio.file.Files.writeString(
            java.nio.file.Paths.get('check.txt'),
            Integer.toHexString(args[0].codePointAt(0)) + ' ' + Integer.toHexString(args[1].codePointAt(0))
        );
    }
}