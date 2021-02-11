package fr.istic.ia.tp1;

import java.util.*;

/**
 * Implementation of the English Draughts game.
 * @author vdrevell
 *
 */
public class EnglishDraughts extends Game {
	/**
	 * The checker board
	 */
	public CheckerBoard board;

	/**
	 * The {@link PlayerId} of the current player
	 * {@link PlayerId#ONE} corresponds to the whites
	 * {@link PlayerId#TWO} corresponds to the blacks
	 */
	PlayerId playerId;

	/**
	 * The current game turn (incremented each time the whites play)
	 */
	int nbTurn;

	/**
	 * The number of consecutive moves played only with kings and without capture
	 * (used to decide equality)
	 */
	int nbKingMovesWithoutCapture;

	/**
	 * The default constructor: initializes a game on the standard 8x8 board.
	 */
	public EnglishDraughts() {
		this(8);
	}

	/**
	 * Constructor with custom boardSize (to play on a boardSize x boardSize checkerBoard).
	 * @param boardSize See {@link CheckerBoard#CheckerBoard(int)} for valid board sizes.
	 */
	public EnglishDraughts(int boardSize) {
		this.board = new CheckerBoard(boardSize);
		this.playerId = PlayerId.ONE;
		this.nbTurn = 1;
		this.nbKingMovesWithoutCapture = 0;
	}

	/**
	 * Store the last move<br>
	 *     Useful to retrieve bestMove from children nodes in MCTS
	 */
	private Move lastMove;

	/**
	 * Copy constructor
	 * @param d The game to copy
	 */
	EnglishDraughts(EnglishDraughts d) {
		this.board = d.board.clone();
		this.playerId = d.playerId;
		this.nbTurn = d.nbTurn;
		this.nbKingMovesWithoutCapture = d.nbKingMovesWithoutCapture;
		this.lastMove = d.lastMove;
	}

	@Override
	public EnglishDraughts clone() {
		return new EnglishDraughts(this);
	}

	@Override
	public String toString() {
		StringBuffer sb = new StringBuffer();
		sb.append(nbTurn);
		sb.append(". ");
		sb.append(this.playerId==PlayerId.ONE?"W":"B");
		sb.append(":");
		sb.append(board.toString());
		return sb.toString();
	}

	@Override
	public String playerName(PlayerId playerId) {
		switch (playerId) {
			case ONE:
				return "Player with the whites";
			case TWO:
				return "Player with the blacks";
			case NONE:
			default:
				return "Nobody";
		}
	}

	@Override
	public String view() {
		return board.boardView() + "Turn #" + nbTurn + ". " + playerName(playerId) + " plays.\n";
	}

	/**
	 * Check if a tile is empty
	 * @param square Tile number
	 * @return true if there's no pawn on a tile, else false
	 */
	boolean isEmpty(int square) {
		return this.board.isEmpty(square);
	}

	/**
	 * Check if a tile is owned by adversary
	 * @param square Tile number
	 * @return true if the tile is owned by adversary, else false
	 */
	boolean isAdversary(int square) {
		return !this.isEmpty(square) && !this.isMine(square);
	}

	/**
	 * Check if a tile is owned by the current player
	 * @param square Tile number
	 * @return true if there's a pawn of the current player on this tile, else false
	 */
	boolean isMine(int square) {
		if(this.playerId.equals(PlayerId.ONE)){
			return this.board.isWhite(square);
		}
		else{
			return this.board.isBlack(square);
		}
	}

	/**
	 * Retrieve the list of positions of the pawns owned by the current player
	 * @return The list of current player pawn positions
	 */
	ArrayList<Integer> myPawns() {
		if(this.playerId.equals(PlayerId.ONE)){
			return this.board.getWhitePawns();
		}
		else{
			return this.board.getBlackPawns();
		}
	}

	/**
	 * Generate the list of possible moves
	 * - first check moves with captures
	 * - if no capture possible, return displacement moves
	 */
	@Override
	public List<Move> possibleMoves() {
		//
		// Advice:
		// create two auxiliary functions :
		// - one for jump moves from a given position, with capture (and multi-capture).
		//    Use recursive calls to explore all multiple capture possibilities
		// - one function that returns the displacement moves from a given position (without capture)
		//
		ArrayList<Move> result = new ArrayList<>();

		for(int pawn : myPawns()){	//pour chaque pion du joueur actuel

			result.addAll(possibleCatchMoves(pawn, board.getCheckerType(pawn),new HashSet<>()));
		}

		if(result.isEmpty()){ //si aucune capture possible
			for(int pawn : myPawns()){
				result.addAll(possibleDisplacementMoves(pawn));
			}
		}

		return result;
	}

	/**
	 * Computes all the possible catch moves, simple and multiple ones
	 * @param pos position of current pawn
	 * @param checkerType , if the checker is white, black, king...
	 * @param caughtChecker , list of checker already taken
	 * @return list of possible capture moves
	 */
	private List<Move> possibleCatchMoves(int pos, byte checkerType, Set<Integer> caughtChecker){
		List<Move> result = new ArrayList<>();
		List<Integer> destJump = destJumpList(pos,checkerType,caughtChecker); //list of capture directions

		//Remove already explored solutions
		destJump.removeAll(caughtChecker);

		for(int dest : destJump){ //pour toutes les captures possibles

			//Copy arrayList to propagate list of caughtChecker checkers, different for each move
			Set<Integer> caughtCheckerPropagate =  new HashSet<>(caughtChecker);
			caughtCheckerPropagate.add(dest);

			//get to know if it can be a multiple move
			List<Move> movesPrisesDest = possibleCatchMoves(dest, checkerType,caughtCheckerPropagate);

			if (movesPrisesDest.isEmpty()){ //si mono capture
				result.add(createAMove(pos,dest)); //add move to list
			}
			else{ //multiple capture
				for(Move moveDest : movesPrisesDest){
					DraughtsMove mv = (DraughtsMove) moveDest;
					result.add(createAMultipleMove(pos,mv)); //prepend the current pos to the next move
				}
			}
		}
		return result;
	}

	/**
	 * Computes all simple capture positions possible from the given square
	 * (check diagonal, max array.size = 4)
	 * Helper for possibleCatchMoves
	 * @param start actual pawn square
	 * @param checkerType , if the checker is white, black, king...
	 * @param caughtChecker , list of checker already taken
	 * @return list of all capture positions
	 */
	private List<Integer> destJumpList(int start, byte checkerType, Set<Integer> caughtChecker){
		List<Integer> result = new ArrayList<>();

		//if it's white's turn or a king
		if(checkerType==CheckerBoard.WHITE_CHECKER || checkerType==CheckerBoard.WHITE_KING || checkerType==CheckerBoard.BLACK_KING) {
			int upLeftTile = this.board.neighborUpLeft(start);
			int upLeftx2Tile = this.board.neighborUpLeft(upLeftTile);
			int upRightTile = this.board.neighborUpRight(start);
			int upRightx2Tile = this.board.neighborUpRight(upRightTile);

			//Check if a catch can be made
			result.addAll(catchPosIfExist(caughtChecker, upLeftTile, upLeftx2Tile));
			result.addAll(catchPosIfExist(caughtChecker, upRightTile, upRightx2Tile));
		}

//if it's black turn or a king
		if(checkerType==CheckerBoard.BLACK_CHECKER || checkerType==CheckerBoard.BLACK_KING || checkerType==CheckerBoard.WHITE_KING){
			int downLeftTile = this.board.neighborDownLeft(start);
			int downLeftx2Tile = this.board.neighborDownLeft(downLeftTile);
			int downRightTile = this.board.neighborDownRight(start);
			int downRightx2Tile = this.board.neighborDownRight(downRightTile);

			result.addAll(catchPosIfExist(caughtChecker, downLeftTile, downLeftx2Tile));
			result.addAll(catchPosIfExist(caughtChecker, downRightTile, downRightx2Tile));
		}

		return result;
	}

	/**
	 * If the checker exit and can be caught, return it
	 * @param caughtChecker, the list of already caught checker
	 * @param diagTile, the tile in diagonal, should be adversary
	 * @param diagx2Tile, the tile after (still in diagonal), shoulb be empty
	 * @return the list containing the move (so it can be added in destJumpList)
	 */
	private List<Integer> catchPosIfExist(Set<Integer> caughtChecker, int diagTile, int diagx2Tile) {
		ArrayList<Integer> result = new ArrayList<>();
		//Check if tile has't been caught already in this move
		if (!caughtChecker.contains(diagTile)) {
			//Check if both tiles exist, and the first should be an adversary, the second should be empty
			if (board.tileExist(diagTile) && this.isAdversary(diagTile) && board.tileExist(diagx2Tile) && this.isEmpty(diagx2Tile)) { //up left capture condition
				result.add(diagx2Tile);
			}
		}
		return  result;
	}

	/**
	 * Generate all possible displacement moves for the player
	 * @return the list of possible displacement moves
	 */
	private List<Move> possibleDisplacementMoves(int start) {
		ArrayList<Move> moves = new ArrayList<>();

		//If white (or if king), check move up
		if(this.board.isWhite(start) || this.board.isKing(start)){
			int upLeftTile = this.board.neighborUpLeft(start);
			int upRightTile = this.board.neighborUpRight(start);

			//if free (check != 0 if in bounds)
			if(this.board.tileExist(upLeftTile) && this.board.isEmpty(upLeftTile)){
				//Create a move	& add it to list
				moves.add(createAMove(start, upLeftTile));
			}

			if(this.board.tileExist(upRightTile) && this.board.isEmpty(upRightTile)){
				//Create a move	& add it to list
				moves.add(createAMove(start, upRightTile));
			}

		}
		//If black (or if king), check move down
		if(this.board.isBlack(start) || this.board.isKing(start)){
			int downLeftTile = this.board.neighborDownLeft(start);
			int downRightTile = this.board.neighborDownRight(start);

			//if free
			if(this.board.tileExist(downLeftTile) && this.board.isEmpty(downLeftTile)){
				//Create a move	& add it to list
				moves.add(createAMove(start, downLeftTile));
			}

			//if free
			if(this.board.tileExist(downRightTile) && this.board.isEmpty(downRightTile)){
				//Create a move	& add it to list
				moves.add(createAMove(start, downRightTile));
			}
		}

		return moves;
	}

	/**
	 * Create a move will create a DraughtMove Object
	 * @param start, the start position
	 * @param destination, the destination position
	 */
	private Move createAMove(int start, int destination) {
		DraughtsMove dMove = new DraughtsMove();
		dMove.add(start);
		dMove.add(destination);
		return dMove;
	}

	/**
	 * Create a move with multiple coordinates
	 * @param start the start position
	 * @param dests the destinations
	 * @return the move with all the coordinates
	 */
	private DraughtsMove createAMultipleMove(int start, DraughtsMove dests){
		DraughtsMove result = new DraughtsMove();
		result.add(start);
		result.addAll(dests);
		return result;
	}

	/**
	 * Play method
	 * Check if (in that order) :
	 * - Player is valid
	 * - Convert chosen move to DraughtsMove
	 * - Move pawn
	 * - Check if adversary pawn(s) captured
	 * - (optional) Promote king if needed
	 * - Change playerId for next player
	 * - Increment number of turn
	 * - (optional) Increment nbKingMovesWithoutCapture if needed
	 * @param aMove, the move to play
	 */
	@Override
	public void play(Move aMove) {
		// Player should be valid
		if (playerId == PlayerId.NONE)
			return;
		// We will cast Move to DraughtsMove (kind of ArrayList<Integer>
		if (!(aMove instanceof DraughtsMove))
			return;
		// Cast and apply the move
		DraughtsMove move = (DraughtsMove) aMove;

		//init state to check "nbKingMovesWithoutCapture" later
		boolean hasBeenCaptured = false; //Keep track if a pawn was captured
		boolean isKing = this.board.isKing(move.getStartPosition()); //check if the pawn is king before turn

		// Move pawn and capture opponents
		for(int i = 1; i < move.size(); i++){
			int startTile = move.get(i-1);
			int destTile = move.get(i);
			//move
			this.board.movePawn(startTile,destTile);

			//check if pawn exist in between
			int inBetweenTile = this.board.squareBetween(startTile,destTile);
			//take it if exist
			if (this.board.tileExist(inBetweenTile) && !this.board.isEmpty(inBetweenTile)){
				this.board.removePawn(inBetweenTile);
				hasBeenCaptured = true;
			}
		}

		//update last move
		lastMove = aMove;

		// Promote to king if the pawn ends on the opposite of the board
		int currentTile = move.getEndPosition();
		if(this.playerId.equals(PlayerId.ONE) && this.board.lineOfSquare(currentTile) == this.board.size-1){
			this.board.crownPawn(currentTile);
		}
		else if(this.playerId.equals(PlayerId.TWO) && this.board.lineOfSquare(currentTile) == 0){
			this.board.crownPawn(currentTile);
		}

		// Next player
		this.playerId = getAdversaryId();

		// Update nbTurn
		nbTurn++;

		// Keep track of successive moves with kings without capture
		if(isKing && !hasBeenCaptured){
			nbKingMovesWithoutCapture++;
		}
		else { //reset counter
			nbKingMovesWithoutCapture = 0;
		}

	}

	/*
	NOTE
	Si j'ai bien compris, le move doit etre possible avant qu'il soit passé en parametre
	 */

	@Override
	public PlayerId player() {
		return playerId;
	}

	/**
	 * Get the winner (or null if the game is still going)
	 * Victory conditions are :
	 * - adversary with no more pawns or no move possibilities
	 * Null game condition (return PlayerId.NONE) is
	 * - more than 25 successive moves of only kings and without any capture
	 */
	@Override
	public PlayerId winner() {

		// return the winner ID if possible
		if(this.myPawns().size() == 0 || this.possibleMoves().size()==0){
			return getAdversaryId();
		}

		// return PlayerId.NONE if the game is null
		else if (this.nbKingMovesWithoutCapture == 25){
			return PlayerId.NONE;
		}

		// Return null is the game has not ended yet
		return null;
	}

	/**
	 * Get the id of the next player
	 * @return ONE if TWO, and TWO if ONE
	 */
	private PlayerId getAdversaryId(){
		return this.playerId == PlayerId.ONE ? PlayerId.TWO : PlayerId.ONE;
	}

/**
 * Class representing a move in the English draughts game
 * A move is an ArrayList of Integers, corresponding to the successive tile numbers (Manouri notation)
 * toString is overriden to provide Manouri notation output.
 * @author vdrevell
 *
 */
public class DraughtsMove extends ArrayList<Integer> implements Game.Move {

	private static final long serialVersionUID = -8215846964873293714L;

	@Override
	public String toString() {
		Iterator<Integer> it = this.iterator();
		Integer from = it.next();
		StringBuffer sb = new StringBuffer();
		sb.append(from);
		while (it.hasNext()) {
			Integer to = it.next();
			if (board.neighborDownLeft(from)==to || board.neighborUpLeft(from)==to
					|| board.neighborDownRight(from)==to || board.neighborUpRight(from)==to) {
				sb.append('-');
			}
			else {
				sb.append('x');
			}
			sb.append(to);
			from = to;
		}
		return sb.toString();
	}

	/**
	 * Get first element of DraughtsMove
	 * @return the first element
	 */
	public int getStartPosition(){
		return this.get(0);
	}

	/**
	 * Get last element of DraughtsMove
	 * @return the last element
	 */
	public int getEndPosition(){
		return this.get(this.size()-1);
	}

}

	/**
	 * Get the last move played in the game
	 * @return the last move played
	 */
	public Move getLastMove() {
		return lastMove;
	}
}
