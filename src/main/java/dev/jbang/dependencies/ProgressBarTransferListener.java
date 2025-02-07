package dev.jbang.dependencies;

import org.eclipse.aether.transfer.AbstractTransferListener;
import org.eclipse.aether.transfer.TransferCancelledException;
import org.eclipse.aether.transfer.TransferEvent;

import me.tongfei.progressbar.ProgressBar;
import me.tongfei.progressbar.ProgressBarBuilder;

public class ProgressBarTransferListener extends AbstractTransferListener {

	private ProgressBar progressBar;

	public ProgressBarTransferListener() {
		ProgressBarBuilder pbb = ProgressBar.builder()
											.showSpeed()// .hideEta()
											.setInitialMax(-1)
											// .setStyle(ProgressBarStyle.ASCII)
											// .setTaskName("Downloading")
											.setUnit("MB", 1024 * 1024);
		// .setUpdateIntervalMillis(100)
		// .setMaxRenderedLength(100)
		// .showSpeed();
		// or .showSpeed(new DecimalFormat("#.##")) to customize speed display
		// .setEtaFunction(state -> ...)

		progressBar = pbb.build();
	}

	public void next(String id) {
		// progressBar.setExtraMessage(id);
	}

	@Override
	public void transferInitiated(TransferEvent event) throws TransferCancelledException {
		// System.err.println("transferInitiated " + event);

	}

	@Override
	public void transferStarted(TransferEvent event) throws TransferCancelledException {
		// System.err.println("transferStarted " + event);
		long contentLength = event.getResource().getContentLength();

		if (contentLength < 0) {
			contentLength = -1;
		}

		long max = progressBar.getMax() + Math.max(0, contentLength);

		progressBar.maxHint(max);

		progressBar.setExtraMessage(event.getResource().getFile().getName());
	}

	@Override
	public void transferProgressed(TransferEvent event) throws TransferCancelledException {
		// System.err.println("transferProgressed " + event);
		// progressBar.maxHint(event.getResource().getContentLength() < 0 ? -1 :
		// event.getResource().getContentLength());
		progressBar.stepBy(event.getDataLength());
	}

	@Override
	public void transferSucceeded(TransferEvent event) {
		// System.out.println("transferSucceeded " + event);
		// progressBar.close();
	}

	@Override
	public void transferFailed(TransferEvent event) {
		// System.out.println("transferFailed " + event);
		// progressBar.close();
	}
}
