package dev.jbang.search;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Function;

import org.jline.keymap.BindingReader;
import org.jline.keymap.KeyMap;
import org.jline.terminal.Terminal;
import org.jline.utils.AttributedString;
import org.jline.utils.AttributedStringBuilder;
import org.jline.utils.InfoCmp.Capability;

class Combobox<T> {
	private final Terminal terminal;
	private final BindingReader reader;
	private final KeyMap<String> keys = new KeyMap<>();
	private final Map<String, ComboboxHandler<T>> eventHandlers = new HashMap<>();

	private int selectedIndex = 0;
	private List<T> matches = new ArrayList<>();
	private StringBuilder qb = new StringBuilder();
	private Function<String, List<T>> filter;
	private Function<T, AttributedString> itemRenderer = (t) -> new AttributedString(t.toString());
	private BiFunction<T, List<T>, Integer> selector = (item, matches) -> matches.indexOf(item);
	private AttributedString prefix = new AttributedString("Search: ");

	Combobox(Terminal terminal, String initialQuery) {
		this.terminal = terminal;
		reader = new BindingReader(terminal.reader());
		this.itemRenderer = (t) -> new AttributedString(t.toString());
		this.qb = new StringBuilder(initialQuery);

		// Define key bindings
		keys.setNomatch("nomatch"); // Any printable char
		handle("up", "\033[A", this::selectionUp); // Arrow up
		handle("down", "\033[B", this::selectionDown); // Arrow down
		handle("backspace", "\177", this::delete);
		handle("enter", "\r", this::select);
		handle("nomatch", "\u0003", this::update); // Ctrl-C
	}

	public ComboboxResult<T> prompt() {
		this.matches = this.filter.apply(this.qb.toString());

		ComboboxResult<T> result = null;
		while (result == null) {
			this.render();
			result = this.waitInput();
		}

		return result;
	}

	public void handle(String eventName, String key, ComboboxHandler<T> handler) {
		this.keys.bind(eventName, key);
		this.eventHandlers.put(eventName, handler);
	}

	private ComboboxResult<T> waitInput() {
		String op = reader.readBinding(keys);
		ComboboxHandler<T> handler = this.eventHandlers.get(op);

		if (handler == null) {
			return null;
		}

		ComboboxResult<T> selection = null;
		for (ComboboxAction action : handler.apply(new InputEvent<>(reader, qb, selectedIndex, matches))) {
			if (action instanceof UpdateInput) {
				UpdateInput updateInput = (UpdateInput) action;
				this.qb = updateInput.sb();
			} else if (action instanceof ReturnSelection) {
				@SuppressWarnings("unchecked")
				ReturnSelection<T> returnSelection = (ReturnSelection<T>) action;
				selection = new ComboboxResult<>(qb.toString(), returnSelection.selected());
			} else if (action instanceof SelectedIndex) {
				SelectedIndex selectedIndexAction = (SelectedIndex) action;
				this.selectedIndex = selectedIndexAction.selectedIndex();
			} else if (action instanceof UpdateCompletions) {
				this.matches = this.filter.apply(this.qb.toString());
			} else if (action instanceof SelectItem<?>) {
				@SuppressWarnings("unchecked")
				SelectItem<T> selectItem = (SelectItem<T>) action;
				int candidateIndex = selector.apply(selectItem.item(), this.matches);
				if (candidateIndex != -1) {
					this.selectedIndex = candidateIndex;
				}
			}
		}

		return selection;
	}

	public AttributedString prefix() {
		return prefix;
	}

	public void withPrefix(AttributedString prefix) {
		this.prefix = prefix;
	}

	public void filterCompletions(Function<String, List<T>> filter) {
		this.filter = filter;
	}

	public void selector(BiFunction<T, List<T>, Integer> selector) {
		this.selector = selector;
	}

	public void renderItem(Function<T, AttributedString> itemRenderer) {
		this.itemRenderer = itemRenderer;
	}

	public void render() {
		terminal.puts(Capability.clear_screen);
		AttributedString header = new AttributedStringBuilder().append(this.prefix).append(qb).toAttributedString();
		terminal.writer().println(header.toAnsi(terminal));
		terminal.writer().println(new String(new char[this.prefix.length()]).replace("\0", "-"));
		for (int i = 0; i < matches.size(); i++) {
			if (i == selectedIndex) {
				terminal.writer().println("> " + itemRenderer.apply(matches.get(i)));
			} else {
				terminal.writer().println("   " + itemRenderer.apply(matches.get(i)));
			}
		}

		// Handle newlines in prefix and qb so the cursor stays in the right spot
		String[] lines = header.toString().split("\\R", -1); // "\\R" matches any newline, -1 keeps trailing empty lines
		int line = lines.length - 1;
		int col = lines[lines.length - 1].length();
		terminal.puts(Capability.cursor_address, line, header.columnLength());
		terminal.flush();
	}

	private List<ComboboxAction> selectionUp(InputEvent<T> e) {
		if (e.selectedIndex() > 0)
			return Collections.<ComboboxAction>singletonList(new SelectedIndex(e.selectedIndex() - 1));

		return Collections.emptyList();
	}

	private List<ComboboxAction> selectionDown(InputEvent<T> e) {
		if (e.selectedIndex() < e.matches().size() - 1)
			return Collections.<ComboboxAction>singletonList(new SelectedIndex(e.selectedIndex() + 1));

		return Collections.emptyList();
	}

	private List<ComboboxAction> update(InputEvent<T> e) {
		char ch = e.reader().getLastBinding().charAt(0);
		e.query().append(ch);
		List<ComboboxAction> actions = new ArrayList<>();
		actions.add(new UpdateInput(e.query()));
		actions.add(new UpdateCompletions());
		return actions;
	}

	private List<ComboboxAction> delete(InputEvent<T> e) {
		if (e.query().length() > 0) {
			StringBuilder newSb = e.query().deleteCharAt(e.query().length() - 1);
			List<ComboboxAction> actions = new ArrayList<>();
			actions.add(new UpdateInput(newSb));
			actions.add(new UpdateCompletions());
			return actions;
		}

		return Collections.emptyList();
	}

	private List<ComboboxAction> select(InputEvent<T> e) {
		if (e.matches().size() > 0) {
			return Collections.<ComboboxAction>singletonList(new ReturnSelection<>(e.matches().get(e.selectedIndex())));
		}

		return Collections.emptyList();
	}

	public static final class ComboboxResult<T> {
		private final String query;
		private final T selection;

		public ComboboxResult(String query, T selection) {
			this.query = query;
			this.selection = selection;
		}

		public String query() {
			return query;
		}

		public T selection() {
			return selection;
		}
	}

	public static final class InputEvent<T> {
		private final BindingReader reader;
		private final StringBuilder query;
		private final int selectedIndex;
		private final List<T> matches;

		public InputEvent(BindingReader reader, StringBuilder query, int selectedIndex, List<T> matches) {
			this.reader = reader;
			this.query = query;
			this.selectedIndex = selectedIndex;
			this.matches = matches;
		}

		public BindingReader reader() {
			return reader;
		}

		public StringBuilder query() {
			return query;
		}

		public int selectedIndex() {
			return selectedIndex;
		}

		public List<T> matches() {
			return matches;
		}
	}

	public interface ComboboxAction {
	}

	public static final class SelectedIndex implements ComboboxAction {
		private final int selectedIndex;

		public SelectedIndex(int selectedIndex) {
			this.selectedIndex = selectedIndex;
		}

		public int selectedIndex() {
			return selectedIndex;
		}
	}

	public static final class SelectItem<T> implements ComboboxAction {
		private final T item;

		public SelectItem(T item) {
			this.item = item;
		}

		public T item() {
			return item;
		}
	}

	public static final class UpdateCompletions implements ComboboxAction {
	}

	public static final class ReturnSelection<T> implements ComboboxAction {
		private final T selected;

		public ReturnSelection(T selected) {
			this.selected = selected;
		}

		public T selected() {
			return selected;
		}
	}

	public static final class UpdateInput implements ComboboxAction {
		private final StringBuilder sb;

		public UpdateInput(StringBuilder sb) {
			this.sb = sb;
		}

		public StringBuilder sb() {
			return sb;
		}
	}

	public interface ComboboxHandler<T> extends Function<InputEvent<T>, List<ComboboxAction>> {
	}
}