package votebox.events;

import sexpression.*;
import sexpression.stream.InvalidVerbatimStreamException;

/**
 * This is an event that gets fired when the supervisor spoils a voter's ballot
 *
 * @author Matt Bernhard
 */
@SuppressWarnings("unused")
public class SpoilBallotEvent extends ABallotEvent {

    /**
     * Matcher for the SpoilBallotEvent
     */
    private static MatcherRule MATCHER = new MatcherRule() {
        private ASExpression pattern = new ListExpression(StringExpression.makeString("spoil-ballot"),
                StringWildcard.SINGLETON, StringWildcard.SINGLETON, Wildcard.SINGLETON);

        public IAnnounceEvent match(int serial, ASExpression sexp) {
            ASExpression res = pattern.match(sexp);

            if(res != NoMatch.SINGLETON) {
                ListExpression list = (ListExpression) sexp;

                ASExpression nonce = list.get(1);

                byte[] ballot = list.get(2).toVerbatim();

                String bid = list.get(3).toString();

                String precinct = list.get(4).toString();

                return new SpoilBallotEvent(serial, nonce, ballot, bid, precinct);
            }

            return null;
        }

    };



    /**
     * Constructs a new SpoilBallotEvent
     *
     * @param serial the serial number of the sender
     * @param bid the ballot to be spoiled
     * @param ballot the encrypted copy of the ballot being spoiled
     * @param nonce  the nonce of the ballot
     */
    public SpoilBallotEvent(int serial, ASExpression nonce,  byte[] ballot, String bid, String precinct) {
        super(serial, nonce,ballot, bid, precinct);
    }

    /** @return the matcher rule */
    public static MatcherRule getMatcher() {
        return MATCHER;
    }

    /**
     * @see votebox.events.IAnnounceEvent#fire(votebox.events.VoteBoxEventListener)
     */
    public void fire(VoteBoxEventListener l) {
        l.spoilBallot(this);
    }

    /**
     * @see votebox.events.IAnnounceEvent#toSExp()
     */
    public ASExpression toSExp() {
        try {
            return new ListExpression(StringExpression.makeString("spoil-ballot"), getNonce(), StringExpression.make(getBID()), ASExpression.makeVerbatim(getBallot()));
        } catch (InvalidVerbatimStreamException e) {
            throw new RuntimeException(e);
        }
    }
}
