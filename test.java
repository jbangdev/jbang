///usr/bin/env jbang "$0" "$@" ; exit $?

//usr/bin/env jbang "$0" "$@" ; exit $?

import java.util.concurrent.TimeUnit;

public class test {
    // ANSI escape codes
    static final String RESET = "\u001B[0m";
    static final String YELLOW = "\u001B[33m";
    static final String BLUE = "\u001B[34m";
    static final String WHITE = "\u001B[37m";
    static final String RED = "\u001B[31m";
    static final String CYAN = "\u001B[36m";
    static final String MOVE_CURSOR_START = "\r";

    // Pac-Man and ghosts unicode
    static final String PACMAN_OPEN = YELLOW + "\uD83D\uDD36" + RESET;    // Use colored circle as open mouth
    static final String PACMAN_CLOSED = YELLOW + "\u25CF" + RESET;       // Use filled circle as closed mouth
    static final String DOT = WHITE + "\u2022" + RESET;
    static final String BIG_DOT = WHITE + "\u25CF" + RESET;
    static final String GHOST_RED = RED + "\uD83D\uDC7B" + RESET;        // 👻 Ghost red
    static final String GHOST_CYAN = CYAN + "\uD83D\uDC7B" + RESET;      // 👻 Ghost cyan
    static final String GHOST_BLUE = BLUE + "\uD83D\uDC7B" + RESET;      // 👻 Ghost blue
    static final String GHOST_WHITE = WHITE + "\uD83D\uDC7B" + RESET;    // 👻 Ghost white

    public static void main(String[] args) throws Exception {
        int dots = 20;
        String[] ghosts = {GHOST_RED, GHOST_CYAN, GHOST_RED, GHOST_CYAN};
        int delay = 120; // ms
        boolean mouthOpen = true;

        // 1. Pac-Man eats small dots
        for (int i = 0; i <= dots; i++) {
            StringBuilder sb = new StringBuilder();
            sb.append(MOVE_CURSOR_START);
            sb.append(" "); // left padding
            // Pac-Man
            sb.append(mouthOpen ? PACMAN_OPEN : PACMAN_CLOSED);
            sb.append(" ");
            // Dots
            for (int j = 0; j < dots; j++) {
                if (j < i) {
                    sb.append(" "); // eaten
                } else {
                    sb.append(DOT);
                }
            }
            // Big dot
            if (i < dots) {
                sb.append(" ");
                sb.append(BIG_DOT);
            } else {
                sb.append(" ");
                sb.append(" "); // eaten big dot
            }
            // Ghosts
            sb.append("   ");
            for (String ghost : ghosts) {
                sb.append(ghost);
                sb.append(" ");
            }
            System.out.print(sb.toString());
            System.out.flush();
            mouthOpen = !mouthOpen;
            Thread.sleep(delay);
        }

        // 2. Pac-Man eats big dot, ghosts turn blue/white (frightened)
        for (int t = 0; t < 6; t++) {
            StringBuilder sb = new StringBuilder();
            sb.append(MOVE_CURSOR_START);
            sb.append(" ");
            sb.append(mouthOpen ? PACMAN_OPEN : PACMAN_CLOSED);
            sb.append(" ");
            for (int j = 0; j < dots; j++) sb.append(" ");
            sb.append("  "); // eaten big dot
            sb.append(t % 2 == 0 ? GHOST_BLUE : GHOST_WHITE);
            sb.append(" ");
            sb.append(t % 2 == 0 ? GHOST_WHITE : GHOST_BLUE);
            sb.append(" ");
            sb.append(t % 2 == 0 ? GHOST_BLUE : GHOST_WHITE);
            sb.append(" ");
            sb.append(t % 2 == 0 ? GHOST_WHITE : GHOST_BLUE);
            System.out.print(sb.toString());
            System.out.flush();
            mouthOpen = !mouthOpen;
            Thread.sleep(150);
        }

        // 3. Pac-Man runs left, eating ghosts one by one
        for (int pac = dots + 2; pac >= 5; pac--) {
            StringBuilder sb = new StringBuilder();
            sb.append(MOVE_CURSOR_START);
            // Padding before Pac-Man
            for (int j = 0; j < pac; j++) sb.append(" ");
            sb.append(mouthOpen ? PACMAN_OPEN : PACMAN_CLOSED);
            sb.append(" ");
            // Remaining ghosts (become blank as eaten)
            int ghostsLeft = (pac-5) / 3;
            for (int i = 0; i < 4; i++) {
                if (i < ghostsLeft) {
                    sb.append(GHOST_BLUE);
                } else {
                    sb.append("  ");
                }
            }
            System.out.print(sb.toString());
            System.out.flush();
            mouthOpen = !mouthOpen;
            Thread.sleep(130);
        }

        // End animation (Pac-Man happy)
        StringBuilder sb = new StringBuilder();
        sb.append(MOVE_CURSOR_START);
        for (int j = 0; j < dots+7; j++) sb.append(" ");
        sb.append(YELLOW + "\u263A" + RESET); // Pac-Man smiling
        System.out.print(sb.toString());
        System.out.flush();
        System.out.println();
    }
}
