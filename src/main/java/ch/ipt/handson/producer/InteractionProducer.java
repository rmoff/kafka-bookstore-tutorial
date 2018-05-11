package ch.ipt.handson.producer;

import ch.ipt.handson.event.*;
import com.google.common.util.concurrent.RateLimiter;
import io.confluent.kafka.serializers.AbstractKafkaAvroSerDeConfig;
import io.confluent.kafka.serializers.KafkaAvroSerializer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;

import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class InteractionProducer {

    public static final String TOPIC_WEBSITE_INTERACTION = "interaction";
    public static final String TOPIC_ORDER = "order";
    public static final String TOPIC_PAYMENT = "payment";


    public static final double VIEWS_PER_SECOND = 100.0;
    public static final double CART_ODDS = .5;
    public static final double ORDER_ODDS = .2;

    static private Producer producer;
    static private Random random = new Random();
    static final Map<Customer, ShoppingCart> carts = new HashMap<>();

    static final RateLimiter rateLimiter = RateLimiter.create(VIEWS_PER_SECOND);

    static final ScheduledExecutorService executor = Executors.newScheduledThreadPool(10);


    public static void main(String[] args) throws InterruptedException {
        setUpProducer();
        produce();
    }

    private static void produce() {
        while (true) {

            Book book = getRandomBook();
            Customer customer = getRandomCustomer();

            ProducerRecord<String, WebsiteInteraction> viewRecord = getInteractionRecord(book, customer, "view");
            producer.send(viewRecord);
            System.out.println("iVIEW " + viewRecord);


            if (random.nextDouble() < CART_ODDS) {
                addToCart(customer, book);

                ProducerRecord<String, WebsiteInteraction> cartRecord = getInteractionRecord(book, customer, "cart");
                producer.send(cartRecord);
                System.out.println("iCART " + cartRecord);

                if (random.nextDouble() < ORDER_ODDS) {

                    ProducerRecord<String, WebsiteInteraction> orderInteractionRecord = getInteractionRecord(book, customer, "cart");
                    producer.send(cartRecord);
                    System.out.println("iORDER " + orderInteractionRecord);

                    ProducerRecord<String, Order> orderRecord = getOrderRecord(customer);
                    producer.send(orderRecord);
                    System.out.println("ORDER " + orderRecord);

                    carts.remove(customer);

                    executor.schedule(() -> produceOrderPayment(orderRecord.value()), Math.round(randomGaussian(60, 30)), TimeUnit.MILLISECONDS);
                }
            }
            rateLimiter.acquire();
        }
    }

    private static void produceOrderPayment(Order order) {
        String transactionId = UUID.randomUUID().toString();
        ProducerRecord paymentRecord = new ProducerRecord<>(
                TOPIC_PAYMENT,
                transactionId,
                Payment.newBuilder()
                        .setOrder(order.getOrderId())
                        .setTransactionId(transactionId)
                        .setTimestamp(new Date().getTime())
                        .setAmount(order.getTotalAmount())
                        .build());
        producer.send(paymentRecord);
        System.out.println("PAY " + paymentRecord);

    }

    private static double randomGaussian(double mean, double standardDeviation) {
        return random.nextGaussian() * standardDeviation + mean;

    }

    private static ProducerRecord<String, Order> getOrderRecord(Customer customer) {
        ShoppingCart shoppingCart = carts.get(customer);
        String id = UUID.randomUUID().toString();
        return new ProducerRecord<>(
                TOPIC_ORDER,
                id,
                Order.newBuilder()
                        .setOrderId(id)
                        .setCustomer(customer)
                        .setBooks(shoppingCart)
                        .setPacket(null)
                        .setTotalAmount(shoppingCart.getBooks().stream().map(book -> book.getPrice()).reduce((p0, p1) -> p0 + p1).orElse(0))
                        .build());
    }

    private static void addToCart(Customer customer, Book book) {
        ShoppingCart cart = carts.get(customer);
        if (cart == null) {
            List<Book> books = new ArrayList<>();
            cart = ShoppingCart.newBuilder()
                    .setBooks(books)
                    .build();
            carts.put(customer, cart);
        }
        cart.getBooks().add(book);
    }

    private static ProducerRecord<String, WebsiteInteraction> getInteractionRecord(Book book, Customer customer, String event) {
        return new ProducerRecord<>(
                TOPIC_WEBSITE_INTERACTION,
                WebsiteInteraction.newBuilder()
                        .setCustomerEmail(customer.getEmail())
                        .setBook(book)
                        .setSession("whatever")
                        .setEvent(event)
                        .build());
    }

//    private static Session getRandomOpenSession() {
//        return Sessions.getOpenSession().get(random.nextInt(CustomersCollection.getCustomers().size()));
//    }

    private static Customer getRandomCustomer() {
        return CustomersCollection.getCustomers().get(random.nextInt(CustomersCollection.getCustomers().size()));
    }

    private static Book getRandomBook() {
        return BooksCollection.getBooks().get(random.nextInt(BooksCollection.getBooks().size()));
    }

//        ArrayList<String> employees = new ArrayList<String>();
//        ArrayList<String> description = new ArrayList<String>();
//        ArrayList<String> expenses = new ArrayList<String>();
//
//        // Randoms for picking value out of ArrayLists
//        Random random_employee = new Random();
//        Random random_description = new Random();
//
//        // Max value of random generated number
//        int maxexpensevalue = 1000;
//
//        // Topic to produce to
//
//        // Time to sleep in ms
//        int timetosleep = 5000;

    // Create new property


//
//        Click
//        Producer<String, Click> producer = new org.apache.kafka.clients.producer.KafkaProducer<>(properties);
//
//        // Fill ArrayLists with values
//        Collections.addAll(employees, "RKO","DAL","PGR","DAL","SHU","TIN","LKE","TSC","ASH","FHE");
//        Collections.addAll(description, "Mittagessen","Abendessen","Training","Bahn","Geschäftsauto","Wochenunterkunft","Flug","Hotel","BYO");
//
    // Do every x milliseconds


    private static void setUpProducer() {
        Properties properties = new Properties();

        properties.setProperty(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, "127.0.0.1:9092");
        properties.setProperty(AbstractKafkaAvroSerDeConfig.SCHEMA_REGISTRY_URL_CONFIG, "http://127.0.0.1:8081");

        properties.setProperty(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        properties.setProperty(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, KafkaAvroSerializer.class.getName());

        properties.setProperty(ProducerConfig.ACKS_CONFIG, "1");
        properties.setProperty(ProducerConfig.RETRIES_CONFIG, "3");
        properties.setProperty(ProducerConfig.LINGER_MS_CONFIG, "1");

        producer = new KafkaProducer(properties);
    }
}
