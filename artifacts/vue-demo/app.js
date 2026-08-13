const { createApp, ref, reactive, computed } = Vue;

let nextId = 1;

createApp({
  data() {
    return {
      title: 'Vue 3 Demo',
      count: 5,
      newTodo: '',
      todos: [
        { id: nextId++, text: 'Acid3 grün', done: true },
        { id: nextId++, text: 'CSS3Test verbessern', done: false },
        { id: nextId++, text: 'Vue 3 rendern', done: false }
      ]
    };
  },
  computed: {
    squared() {
      return this.count * this.count;
    },
    remaining() {
      return this.todos.filter(todo => !todo.done).length;
    },
    footerNote() {
      return this.remaining === 0
        ? 'Keine offenen Aufgaben — gut gemacht!'
        : 'Noch ' + this.remaining + ' Aufgaben offen.';
    }
  },
  methods: {
    increment() {
      this.count++;
    },
    reset() {
      this.count = 0;
    },
    addTodo() {
      const text = this.newTodo.trim();
      if (!text) return;
      this.todos.push({ id: nextId++, text: text, done: false });
      this.newTodo = '';
    },
    removeTodo(todo) {
      const index = this.todos.indexOf(todo);
      if (index >= 0) this.todos.splice(index, 1);
    }
  }
}).mount('#app');
