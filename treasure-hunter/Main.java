import java.util.*;

public class Main {
    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);
        int n = scanner.nextInt();
        int m = scanner.nextInt();

        long[][] height = new long[n][m];
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < m; j++) {
                height[i][j] = scanner.nextLong();
            }
        }

        final long INF = Long.MAX_VALUE / 4;
        long[][] dist = new long[n][m];
        for (int i = 0; i < n; i++) {
            Arrays.fill(dist[i], INF);
        }

        PriorityQueue<Cell> pq = new PriorityQueue<>(Comparator.comparingLong(cell -> cell.time));

        for (int i = 0; i < n; i++) {
            for (int j = 0; j < m; j++) {
                if (height[i][j] == 0) {
                    dist[i][j] = 0;
                    pq.offer(new Cell(i, j, 0));
                }
            }
        }

        int[] dx = {1, -1, 0, 0};
        int[] dy = {0, 0, 1, -1};

        while (!pq.isEmpty()) {
            Cell current = pq.poll();
            if (current.time > dist[current.x][current.y]) {
                continue;
            }

            for (int dir = 0; dir < 4; dir++) {
                int nx = current.x + dx[dir];
                int ny = current.y + dy[dir];
                if (nx < 0 || nx >= n || ny < 0 || ny >= m) {
                    continue;
                }

                long candidate = Math.max(current.time, height[nx][ny]);
                if (candidate < dist[nx][ny]) {
                    dist[nx][ny] = candidate;
                    pq.offer(new Cell(nx, ny, candidate));
                }
            }
        }

        for (int i = 0; i < n; i++) {
            StringBuilder sb = new StringBuilder();
            for (int j = 0; j < m; j++) {
                if (j > 0) {
                    sb.append(' ');
                }
                long time = dist[i][j];
                if (time == INF) {
                    time = height[i][j];
                }
                sb.append(time);
            }
            System.out.println(sb.toString());
        }
        scanner.close();
    }

    private static final class Cell {
        final int x;
        final int y;
        final long time;

        Cell(int x, int y, long time) {
            this.x = x;
            this.y = y;
            this.time = time;
        }
    }
}
