//FILES message.txt

public class Resource {
	public static void main(String[] args) throws Exception {
		Thread worker = new Thread(() -> {
			try {
				System.out.println(new String(Thread.currentThread().getContextClassLoader()
					.getResourceAsStream("message.txt").readAllBytes(), java.nio.charset.StandardCharsets.UTF_8));
			} catch (Exception e) {
				throw new RuntimeException(e);
			}
		});
		worker.start();
		worker.join();
	}
}
